package dk.jamesbabz.madkursus.tools.recipetemplate;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** File-only developer workflow. No database, Git, or deployment operations. */
@lombok.extern.slf4j.Slf4j
public class RecipeTemplateLocalImporter {
    private static final Object IMPORT_LOCK = new Object();
    private static final Pattern VERSION = Pattern.compile("V([0-9]+(?:[._][0-9]+)*)__[A-Za-z0-9_]+\\.(sql|java)");
    private final Path root;
    public RecipeTemplateLocalImporter(Path root) { this.root=root.toAbsolutePath().normalize(); }
    public record Outcome(RecipeTemplateDraftTool.PreparedDraft draft,String migration) {}

    public RecipeTemplateDraftTool.PreparedDraft validate(String json)throws IOException {
        safe("src/main/resources/seed/recipe-templates.json");
        return new RecipeTemplateDraftTool(root).prepare(json);
    }
    public Outcome importDraft(String json)throws IOException {
        synchronized(IMPORT_LOCK) {
            nextVersion(); // Unsafe version history must fail before even creating a lock/staging file.
            // The persistent lock file avoids lock-inode replacement races between app processes.
            Path build=root.resolve("build");Files.createDirectories(build);safe("build");
            Path lock=build.resolve("recipe-template-import.lock");
            if(Files.isSymbolicLink(lock))throw new IOException("Import lock cannot be a symbolic link");
            try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var ignored=channel.lock()) {
                var draft=validate(json); // Never trust the earlier browser validation.
                Path canonical=safe("src/main/resources/seed/recipe-templates.json");
                Path directory=safe("src/main/resources/db/migration");
                String version=nextVersion();
                String filename="V"+version+"__"+(draft.validation().update()?"update_":"add_")+draft.validation().key().toLowerCase(Locale.ROOT)+".sql";
                Path migration=directory.resolve(filename);
                if(Files.exists(migration))throw new IOException("Migration already exists");
                String sql=draft.sql().replace("-- REVIEW CANDIDATE: rename V_NEXT before deployment. Generated deterministically.","-- Generated locally. Review and test before deployment.");
                Path replacement=null,backup=null,stagedSql=null;
                boolean replaced=false,rollbackFailed=false;
                try {
                    replacement=stage(canonical.getParent(),draft.after());
                    backup=stage(canonical.getParent(),draft.before());
                    stagedSql=stage(directory,sql);
                    if(!Files.readString(canonical,StandardCharsets.UTF_8).equals(draft.before())||!nextVersion().equals(version))
                        throw new IOException("Source files changed during import; validate again");
                    Files.move(replacement,canonical,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    replaced=true;
                    publishMigration(stagedSql,migration);
                    return new Outcome(draft,filename);
                } catch(IOException|RuntimeException failure) {
                    if(replaced) {
                        try { Files.move(backup,canonical,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                        catch(IOException rollback) { rollbackFailed=true;failure.addSuppressed(rollback);throw new IOException("Canonical rollback failed; recover from "+backup,failure); }
                    }
                    throw failure;
                } finally {
                    // Never delete a final migration: it may belong to another writer.
                    for(Path temp:Arrays.asList(replacement,stagedSql))cleanup(temp);
                    if(!rollbackFailed)cleanup(backup);
                }
            }
        }
    }
    private void cleanup(Path path) {
        if(path!=null)try{Files.deleteIfExists(path);}catch(IOException failure){log.warn("Could not remove import staging file {}",path,failure);}
    }
    protected void publishMigration(Path staged,Path target)throws IOException {
        // No REPLACE_EXISTING: a concurrently created migration must never be overwritten.
        Files.move(staged,target);
    }
    private Path stage(Path parent,String content)throws IOException {
        Path temp=Files.createTempFile(parent,".recipe-import-",".tmp");
        try {Files.writeString(temp,content,StandardCharsets.UTF_8);return temp;}
        catch(IOException failure){Files.deleteIfExists(temp);throw failure;}
    }
    public String nextVersion()throws IOException {
        Set<String> used=new HashSet<>();BigInteger highest=BigInteger.ZERO;
        for(String folder:List.of("src/main/resources/db/migration","src/main/java/db/migration")) {
            Path dir=safe(folder);
            try(var files=Files.list(dir)) {
                for(Path path:files.toList()) {
                    String name=path.getFileName().toString();
                    if(!name.startsWith("V"))continue;
                    var match=VERSION.matcher(name);
                    if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!match.matches())throw new IOException("Cannot safely determine version: "+name);
                    var parts=new ArrayList<>(Arrays.stream(match.group(1).split("[._]")).map(BigInteger::new).toList());
                    while(parts.size()>1&&parts.getLast().signum()==0)parts.removeLast();
                    String version=parts.stream().map(BigInteger::toString).collect(java.util.stream.Collectors.joining("."));
                    if(!used.add(version))throw new IOException("Duplicate Flyway version: "+version);
                    // Next whole major is greater than every existing numeric subversion too.
                    highest=highest.max(parts.getFirst());
                }
            }
        }
        if(used.isEmpty())throw new IOException("No canonical migrations found");
        return highest.add(BigInteger.ONE).toString();
    }
    private Path safe(String relative)throws IOException {
        Path realRoot=root.toRealPath(),target=root.resolve(relative);
        if(!target.toRealPath().startsWith(realRoot))throw new IOException("Authoring path leaves the configured project");
        return target;
    }
}
