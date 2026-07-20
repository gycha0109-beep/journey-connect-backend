-- Journey Connect DB v2.7 - P2 experiment, statistical evaluation, and release evidence
BEGIN;
CREATE OR REPLACE FUNCTION public.recommendation_p2_is_finite(p_value double precision) RETURNS boolean LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT p_value <> 'NaN'::double precision AND p_value <> 'Infinity'::double precision AND p_value <> '-Infinity'::double precision $$;
CREATE TABLE public.recommendation_p2_experiment_assignment(assignment_id varchar(128) PRIMARY KEY,experiment_id varchar(128) NOT NULL,experiment_version varchar(128) NOT NULL,subject_ref varchar(128) NOT NULL,user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,assignment_unit varchar(32) NOT NULL CHECK(assignment_unit='user'),variant varchar(16) NOT NULL CHECK(variant IN('baseline','treatment')),bucket integer NOT NULL CHECK(bucket BETWEEN 0 AND 9999),assignment_fingerprint varchar(64) NOT NULL CHECK(assignment_fingerprint~'^[0-9a-f]{64}$'),assigned_at timestamptz NOT NULL,producer_build_id varchar(128) NOT NULL,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT recommendation_p2_assignment_id_format CHECK(assignment_id~'^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),CONSTRAINT recommendation_p2_assignment_subject_ref CHECK(subject_ref='user:'||user_id::text),UNIQUE(experiment_id,experiment_version,subject_ref));
CREATE TABLE public.recommendation_p2_experiment_exposure(exposure_id varchar(128) PRIMARY KEY,assignment_id varchar(128) NOT NULL REFERENCES public.recommendation_p2_experiment_assignment(assignment_id) ON DELETE RESTRICT,run_id varchar(128) NOT NULL REFERENCES public.recommendation_run(run_id) ON DELETE RESTRICT,user_id bigint NOT NULL REFERENCES public.app_users(id) ON DELETE RESTRICT,session_id varchar(128) NOT NULL,variant varchar(16) NOT NULL CHECK(variant IN('baseline','treatment')),exposed_at timestamptz NOT NULL,exposure_fingerprint varchar(64) NOT NULL CHECK(exposure_fingerprint~'^[0-9a-f]{64}$'),created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE(assignment_id,run_id));
CREATE TABLE public.recommendation_p2_dataset_snapshot(dataset_snapshot_id varchar(128) PRIMARY KEY,dataset_schema_version varchar(128) NOT NULL,metric_definition_version varchar(128) NOT NULL,experiment_id varchar(128) NOT NULL,experiment_version varchar(128) NOT NULL,observed_from timestamptz NOT NULL,observed_to timestamptz NOT NULL,observation_count integer NOT NULL CHECK(observation_count>=0),canonicalization_version varchar(128) NOT NULL,canonical_payload bytea NOT NULL,payload_size_bytes integer NOT NULL CHECK(payload_size_bytes BETWEEN 0 AND 16777216),content_hash varchar(64) NOT NULL CHECK(content_hash~'^[0-9a-f]{64}$'),created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT recommendation_p2_dataset_window CHECK(observed_from<observed_to),CONSTRAINT recommendation_p2_dataset_size CHECK(octet_length(canonical_payload)=payload_size_bytes),CONSTRAINT recommendation_p2_dataset_hash CHECK(content_hash=public.recommendation_sha256_hex(canonical_payload)));
CREATE TABLE public.recommendation_p2_metric_definition(metric_definition_version varchar(128) NOT NULL,metric_id varchar(128) NOT NULL,metric_role varchar(16) NOT NULL CHECK(metric_role IN('primary','guardrail')),direction varchar(32) NOT NULL CHECK(direction IN('higher_is_better','lower_is_better')),minimum_effect double precision NOT NULL CHECK(minimum_effect>=0 AND public.recommendation_p2_is_finite(minimum_effect)),maximum_allowed_regression double precision NOT NULL CHECK(maximum_allowed_regression>=0 AND public.recommendation_p2_is_finite(maximum_allowed_regression)),attribution_window_seconds bigint NOT NULL CHECK(attribution_window_seconds BETWEEN 1 AND 31536000),numerator_definition varchar(512) NOT NULL,denominator_definition varchar(512) NOT NULL,eligibility_definition varchar(512) NOT NULL,deduplication_definition varchar(512) NOT NULL,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(metric_definition_version,metric_id),CONSTRAINT recommendation_p2_metric_primary_regression CHECK(metric_role<>'primary' OR maximum_allowed_regression=0));
CREATE TABLE public.recommendation_p2_evaluation_run(evaluation_run_id varchar(128) PRIMARY KEY,dataset_snapshot_id varchar(128) NOT NULL REFERENCES public.recommendation_p2_dataset_snapshot(dataset_snapshot_id) ON DELETE RESTRICT,metric_definition_version varchar(128) NOT NULL,evaluation_policy_version varchar(128) NOT NULL,experiment_id varchar(128) NOT NULL,experiment_version varchar(128) NOT NULL,baseline_policy_version varchar(128) NOT NULL,treatment_policy_version varchar(128) NOT NULL,evaluator_build_id varchar(128) NOT NULL,evaluated_at timestamptz NOT NULL,current_state varchar(16) NOT NULL CHECK(current_state IN('draft','shadow','canary','live','hold','rolled_back')),requested_state varchar(16) NOT NULL CHECK(requested_state IN('draft','shadow','canary','live','hold','rolled_back')),operational_approval boolean NOT NULL,final_decision varchar(16) NOT NULL CHECK(final_decision IN('canary','live','hold','rollback')),target_state varchar(16) NOT NULL CHECK(target_state IN('draft','shadow','canary','live','hold','rolled_back')),evaluation_fingerprint varchar(64) NOT NULL CHECK(evaluation_fingerprint~'^[0-9a-f]{64}$'),created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE public.recommendation_p2_metric_result(evaluation_run_id varchar(128) NOT NULL REFERENCES public.recommendation_p2_evaluation_run(evaluation_run_id) ON DELETE RESTRICT,segment varchar(128) NOT NULL,metric_definition_version varchar(128) NOT NULL,metric_id varchar(128) NOT NULL,baseline_count integer NOT NULL CHECK(baseline_count>=0),treatment_count integer NOT NULL CHECK(treatment_count>=0),eligible_exposed_count integer NOT NULL CHECK(eligible_exposed_count>=0),missing_metric_count integer NOT NULL CHECK(missing_metric_count>=0),common_support_rate double precision NOT NULL CHECK(common_support_rate BETWEEN 0 AND 1 AND public.recommendation_p2_is_finite(common_support_rate)),baseline_mean double precision NOT NULL CHECK(public.recommendation_p2_is_finite(baseline_mean)),treatment_mean double precision NOT NULL CHECK(public.recommendation_p2_is_finite(treatment_mean)),raw_effect double precision NOT NULL CHECK(public.recommendation_p2_is_finite(raw_effect)),oriented_effect double precision NOT NULL CHECK(public.recommendation_p2_is_finite(oriented_effect)),effect_size double precision NOT NULL CHECK(public.recommendation_p2_is_finite(effect_size)),confidence_lower double precision NOT NULL CHECK(public.recommendation_p2_is_finite(confidence_lower)),confidence_upper double precision NOT NULL CHECK(public.recommendation_p2_is_finite(confidence_upper)),confidence_level double precision NOT NULL CHECK(confidence_level>0.5 AND confidence_level<1),p_value double precision NOT NULL CHECK(p_value BETWEEN 0 AND 1),adjusted_p_value double precision NOT NULL CHECK(adjusted_p_value BETWEEN 0 AND 1),sample_sufficient boolean NOT NULL,data_quality_pass boolean NOT NULL,performance_pass boolean NOT NULL,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(evaluation_run_id,segment,metric_id),FOREIGN KEY(metric_definition_version,metric_id) REFERENCES public.recommendation_p2_metric_definition(metric_definition_version,metric_id) ON DELETE RESTRICT,CONSTRAINT recommendation_p2_metric_ci_order CHECK(confidence_lower<=confidence_upper));
CREATE TABLE public.recommendation_p2_gate_result(evaluation_run_id varchar(128) NOT NULL REFERENCES public.recommendation_p2_evaluation_run(evaluation_run_id) ON DELETE RESTRICT,gate_id varchar(64) NOT NULL CHECK(gate_id IN('gate_a_contract_integrity','gate_b_data_quality','gate_c_sample_sufficiency','gate_d_performance_guardrail','gate_e_operational_approval')),gate_status varchar(16) NOT NULL CHECK(gate_status IN('pass','fail','hold')),reason_codes jsonb NOT NULL CHECK(jsonb_typeof(reason_codes)='array'),created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(evaluation_run_id,gate_id));
CREATE TABLE public.recommendation_p2_release_decision(decision_id varchar(128) PRIMARY KEY,evaluation_run_id varchar(128) NOT NULL UNIQUE REFERENCES public.recommendation_p2_evaluation_run(evaluation_run_id) ON DELETE RESTRICT,experiment_id varchar(128) NOT NULL,experiment_version varchar(128) NOT NULL,from_state varchar(16) NOT NULL CHECK(from_state IN('draft','shadow','canary','live','hold','rolled_back')),to_state varchar(16) NOT NULL CHECK(to_state IN('draft','shadow','canary','live','hold','rolled_back')),final_decision varchar(16) NOT NULL CHECK(final_decision IN('canary','live','hold','rollback')),actor_ref varchar(128) NOT NULL CHECK(actor_ref~'^(user|system):[A-Za-z0-9._:-]{1,121}$'),reason_code varchar(128) NOT NULL,decided_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX recommendation_p2_assignment_experiment_idx ON public.recommendation_p2_experiment_assignment(experiment_id,experiment_version,variant,assigned_at);CREATE INDEX recommendation_p2_exposure_run_idx ON public.recommendation_p2_experiment_exposure(run_id,exposed_at);CREATE INDEX recommendation_p2_evaluation_experiment_idx ON public.recommendation_p2_evaluation_run(experiment_id,experiment_version,evaluated_at DESC);
CREATE OR REPLACE FUNCTION public.validate_recommendation_p2_exposure_binding()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  assignment_row public.recommendation_p2_experiment_assignment%ROWTYPE;
  run_row public.recommendation_run%ROWTYPE;
  is_p1_treatment_run boolean;
BEGIN
  SELECT * INTO assignment_row
  FROM public.recommendation_p2_experiment_assignment
  WHERE assignment_id = NEW.assignment_id;

  SELECT * INTO run_row
  FROM public.recommendation_run
  WHERE run_id = NEW.run_id;

  SELECT EXISTS (
    SELECT 1
    FROM public.recommendation_p1_policy_assignment p1
    WHERE p1.treatment_run_id = NEW.run_id
  ) INTO is_p1_treatment_run;

  IF assignment_row.user_id <> NEW.user_id
     OR assignment_row.variant <> NEW.variant
     OR run_row.user_id <> NEW.user_id
     OR run_row.session_id <> NEW.session_id
     OR NEW.exposed_at < run_row.reference_time
     OR (NEW.variant = 'baseline' AND is_p1_treatment_run)
     OR (NEW.variant = 'treatment' AND NOT is_p1_treatment_run) THEN
    RAISE EXCEPTION 'P2 experiment exposure binding mismatch' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE OR REPLACE FUNCTION public.validate_recommendation_p2_evaluation_binding() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE d public.recommendation_p2_dataset_snapshot%ROWTYPE;BEGIN SELECT * INTO d FROM public.recommendation_p2_dataset_snapshot WHERE dataset_snapshot_id=NEW.dataset_snapshot_id;IF d.metric_definition_version<>NEW.metric_definition_version OR d.experiment_id<>NEW.experiment_id OR d.experiment_version<>NEW.experiment_version OR NEW.evaluated_at<d.observed_to THEN RAISE EXCEPTION 'P2 evaluation binding mismatch' USING ERRCODE='23514';END IF;RETURN NEW;END $$;
CREATE OR REPLACE FUNCTION public.validate_recommendation_p2_release_decision()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  evaluation_row public.recommendation_p2_evaluation_run%ROWTYPE;
  transition_ok boolean;
  gate_count integer;
  pass_count integer;
  performance_gate_status varchar(16);
  evidence_ok boolean;
BEGIN
  SELECT * INTO evaluation_row
  FROM public.recommendation_p2_evaluation_run
  WHERE evaluation_run_id = NEW.evaluation_run_id;

  transition_ok := CASE NEW.from_state
    WHEN 'draft' THEN NEW.to_state IN ('shadow','hold')
    WHEN 'shadow' THEN NEW.to_state IN ('canary','hold')
    WHEN 'canary' THEN NEW.to_state IN ('live','hold','rolled_back')
    WHEN 'live' THEN NEW.to_state IN ('hold','rolled_back')
    WHEN 'hold' THEN NEW.to_state IN ('shadow','canary','rolled_back')
    WHEN 'rolled_back' THEN NEW.to_state = 'shadow'
    ELSE false
  END;

  SELECT count(*)::integer,
         count(*) FILTER (WHERE gate_status = 'pass')::integer,
         max(gate_status) FILTER (WHERE gate_id = 'gate_d_performance_guardrail')
    INTO gate_count, pass_count, performance_gate_status
  FROM public.recommendation_p2_gate_result
  WHERE evaluation_run_id = NEW.evaluation_run_id;

  evidence_ok := CASE NEW.final_decision
    WHEN 'canary' THEN NEW.to_state = 'canary' AND gate_count = 5 AND pass_count = 5
    WHEN 'live' THEN NEW.to_state = 'live' AND gate_count = 5 AND pass_count = 5
    WHEN 'hold' THEN NEW.to_state = 'hold' AND gate_count = 5 AND pass_count < 5
    WHEN 'rollback' THEN NEW.to_state = 'rolled_back' AND gate_count = 5
                         AND performance_gate_status = 'fail'
    ELSE false
  END;

  IF evaluation_row.experiment_id <> NEW.experiment_id
     OR evaluation_row.experiment_version <> NEW.experiment_version
     OR evaluation_row.current_state <> NEW.from_state
     OR evaluation_row.target_state <> NEW.to_state
     OR evaluation_row.final_decision <> NEW.final_decision
     OR NEW.decided_at < evaluation_row.evaluated_at
     OR NOT transition_ok
     OR NOT evidence_ok THEN
    RAISE EXCEPTION 'P2 release decision binding, evidence, or transition mismatch'
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER recommendation_p2_exposure_binding BEFORE INSERT ON public.recommendation_p2_experiment_exposure FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_p2_exposure_binding();CREATE TRIGGER recommendation_p2_evaluation_binding BEFORE INSERT ON public.recommendation_p2_evaluation_run FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_p2_evaluation_binding();CREATE TRIGGER recommendation_p2_release_decision_binding BEFORE INSERT ON public.recommendation_p2_release_decision FOR EACH ROW EXECUTE FUNCTION public.validate_recommendation_p2_release_decision();
CREATE TRIGGER recommendation_p2_assignment_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_experiment_assignment FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_exposure_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_experiment_exposure FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_dataset_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_dataset_snapshot FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_metric_definition_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_metric_definition FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_evaluation_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_evaluation_run FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_metric_result_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_metric_result FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_gate_result_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_gate_result FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();CREATE TRIGGER recommendation_p2_release_decision_append_only BEFORE UPDATE OR DELETE ON public.recommendation_p2_release_decision FOR EACH ROW EXECUTE FUNCTION public.prevent_recommendation_append_only_mutation();
REVOKE ALL ON public.recommendation_p2_experiment_assignment,public.recommendation_p2_experiment_exposure,public.recommendation_p2_dataset_snapshot,public.recommendation_p2_metric_definition,public.recommendation_p2_evaluation_run,public.recommendation_p2_metric_result,public.recommendation_p2_gate_result,public.recommendation_p2_release_decision FROM PUBLIC,jc_app,jc_auth,jc_admin,jc_security_owner,jc_recommendation;GRANT SELECT,INSERT ON public.recommendation_p2_experiment_assignment,public.recommendation_p2_experiment_exposure,public.recommendation_p2_dataset_snapshot,public.recommendation_p2_metric_definition,public.recommendation_p2_evaluation_run,public.recommendation_p2_metric_result,public.recommendation_p2_gate_result,public.recommendation_p2_release_decision TO jc_recommendation;GRANT SELECT ON public.recommendation_p2_experiment_assignment,public.recommendation_p2_experiment_exposure,public.recommendation_p2_dataset_snapshot,public.recommendation_p2_metric_definition,public.recommendation_p2_evaluation_run,public.recommendation_p2_metric_result,public.recommendation_p2_gate_result,public.recommendation_p2_release_decision TO jc_admin;GRANT EXECUTE ON FUNCTION public.recommendation_p2_is_finite(double precision) TO jc_recommendation,jc_admin;REVOKE UPDATE,DELETE,TRUNCATE ON public.recommendation_p2_experiment_assignment,public.recommendation_p2_experiment_exposure,public.recommendation_p2_dataset_snapshot,public.recommendation_p2_metric_definition,public.recommendation_p2_evaluation_run,public.recommendation_p2_metric_result,public.recommendation_p2_gate_result,public.recommendation_p2_release_decision FROM jc_recommendation,jc_admin;
COMMIT;
