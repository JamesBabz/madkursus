package dk.jamesbabz.madkursus.tools;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.inbound.rest.nutritionadmin.NutritionAdminApiDelegateImpl;
import dk.jamesbabz.madkursus.inbound.rest.dto.ProductTemplateNutritionDTO;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
public final class NutritionEndpointProfileCli{
 public static void main(String[]args)throws Exception{try(var context=new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET).run("--server.port=0","--spring.jpa.properties.hibernate.generate_statistics=true")){var stats=context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();stats.clear();long start=System.nanoTime();List<ProductTemplateNutritionDTO> body=context.getBean(NutritionAdminApiDelegateImpl.class).getProductTemplateNutrition("ALL","").getBody();byte[] json=context.getBean(ObjectMapper.class).writeValueAsBytes(body);long elapsed=(System.nanoTime()-start)/1_000_000;long candidates=body.stream().filter(value->value.getDtuSuggestion()!=null).flatMap(value->value.getDtuSuggestion().getCandidates().stream()).count();System.out.printf("NUTRITION_LIST_PROFILE items=%d candidates=%d bytes=%d ms=%d queries=%d%n",body.size(),candidates,json.length,elapsed,stats.getPrepareStatementCount());}}
}
