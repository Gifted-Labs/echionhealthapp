-- Database optimization script for report search
-- Run this script to create indexes and rebuild search vectors

-- 1. Create GIN index on search_vector for fast full-text search
CREATE INDEX IF NOT EXISTS idx_reports_search_vector 
ON reports USING GIN(search_vector);

-- 2. Create B-tree indexes for filter columns
CREATE INDEX IF NOT EXISTS idx_reports_user_id 
ON reports(user_id);

CREATE INDEX IF NOT EXISTS idx_reports_scan_type 
ON reports(scan_type);

CREATE INDEX IF NOT EXISTS idx_reports_report_type 
ON reports(report_type);

CREATE INDEX IF NOT EXISTS idx_reports_scan_date 
ON reports(scan_date DESC NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_reports_created_at 
ON reports(created_at DESC);

-- 3. Create trigram extension for ILIKE optimization (optional but recommended)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 4. Create trigram indexes for ILIKE searches on text fields
CREATE INDEX IF NOT EXISTS idx_reports_extracted_text_trgm 
ON reports USING GIN(extracted_text gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_reports_patient_name_trgm 
ON reports USING GIN(patient_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_reports_findings_trgm 
ON reports USING GIN(findings gin_trgm_ops);

-- 5. Rebuild all search vectors to ensure they are populated
UPDATE reports
SET search_vector = setweight(to_tsvector('english', coalesce(patient_name,'')), 'A') ||
                    setweight(to_tsvector('english', coalesce(patient_id,'')), 'A') ||
                    setweight(to_tsvector('english', coalesce(findings,'')), 'B') ||
                    setweight(to_tsvector('english', coalesce(impression,'')), 'B') ||
                    setweight(to_tsvector('english', coalesce(clinical_history,'')), 'C') ||
                    setweight(to_tsvector('english', coalesce(recommendation,'')), 'C') ||
                    setweight(to_tsvector('english', coalesce(extracted_text,'')), 'D');

-- 6. Analyze table for query optimizer
ANALYZE reports;

-- Verify indexes
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'reports';
