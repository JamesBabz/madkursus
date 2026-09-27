CREATE TABLE feedback (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('FEEDBACK', 'BUG')),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    description TEXT NOT NULL CHECK (length(trim(description)) > 0 AND length(description) <= 10000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE')),
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
