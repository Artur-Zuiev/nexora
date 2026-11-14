-- Local-demo organization and database-level integrity.
-- Existing demo rows were assigned to local-admin by V2.
-- If old tasks/activities refer to missing leads, this migration FAILS rather than deleting data.
CREATE TABLE organizations (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO organizations (id, name)
VALUES ('local-admin', 'Local development workspace');

-- Require every tenant identifier to refer to a known organization.
ALTER TABLE leads
    ADD CONSTRAINT fk_leads_organization
    FOREIGN KEY (tenant_id) REFERENCES organizations (id);
ALTER TABLE lead_tasks
    ADD CONSTRAINT fk_lead_tasks_organization
    FOREIGN KEY (tenant_id) REFERENCES organizations (id);
ALTER TABLE lead_activities
    ADD CONSTRAINT fk_lead_activities_organization
    FOREIGN KEY (tenant_id) REFERENCES organizations (id);

-- Include tenant id in the referenced key so children cannot point to a
-- lead that belongs to a different organization.
ALTER TABLE leads
    ADD CONSTRAINT uq_leads_tenant_id_id UNIQUE (tenant_id, id);

ALTER TABLE lead_tasks
    ADD CONSTRAINT fk_lead_tasks_same_tenant_lead
    FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id);

ALTER TABLE lead_activities
    ADD CONSTRAINT fk_lead_activities_same_tenant_lead
    FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id);
