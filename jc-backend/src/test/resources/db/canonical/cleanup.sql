TRUNCATE TABLE
  public.recommendation_p2_release_decision,
  public.recommendation_p2_gate_result,
  public.recommendation_p2_metric_result,
  public.recommendation_p2_evaluation_run,
  public.recommendation_p2_metric_definition,
  public.recommendation_p2_dataset_snapshot,
  public.recommendation_p2_experiment_exposure,
  public.recommendation_p2_experiment_assignment,
  public.recommendation_p1_comparison,
  public.recommendation_p1_policy_assignment,
  public.recommendation_p1_profile_snapshot,
  public.recommendation_user_preference,
  public.recommendation_replay_audit,
  public.recommendation_exposure_candidate,
  public.recommendation_behavior_event,
  public.recommendation_exposure_event,
  public.recommendation_run_candidate,
  public.recommendation_run_terminal_candidate,
  public.recommendation_run,
  public.recommendation_snapshot;
DELETE FROM public.reports;
DELETE FROM public.crew_members;
DELETE FROM public.crews;
DELETE FROM public.refresh_tokens;
DELETE FROM public.bookmarks;
DELETE FROM public.post_likes;
DELETE FROM public.comments;
DELETE FROM public.post_tags;
DELETE FROM public.post_places;
DELETE FROM public.post_images;
DELETE FROM public.posts;
DELETE FROM public.places;
DELETE FROM public.follows;
DELETE FROM public.app_users;
