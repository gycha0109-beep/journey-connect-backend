import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { normalizeAdminError } from "./adminErrors";
import { ADMIN_MAX_SEARCH_LENGTH, ADMIN_PAGE_SIZE } from "./adminPolicies";

export default function useAdminListQuery(loader, filterNames = []) {
  const [params, setParams] = useSearchParams();
  const [state, setState] = useState({ loading: true, data: null, error: null });
  const page = Math.max(Number(params.get("page") || 0), 0);
  const search = (params.get("search") || "").slice(0, ADMIN_MAX_SEARCH_LENGTH);
  const filters = useMemo(() => Object.fromEntries(filterNames.map((name) => [name, params.get(name) || ""])), [filterNames, params]);

  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const data = await loader({ page, size: ADMIN_PAGE_SIZE, search, ...filters });
      setState({ loading: false, data, error: null });
    } catch (error) {
      setState({ loading: false, data: null, error: normalizeAdminError(error) });
    }
  }, [loader, page, search, filters]);

  useEffect(() => { load(); }, [load]);

  const update = useCallback((values) => {
    const next = new URLSearchParams(params);
    Object.entries(values).forEach(([key, value]) => value ? next.set(key, String(value)) : next.delete(key));
    if (!("page" in values)) next.delete("page");
    setParams(next);
  }, [params, setParams]);

  return { ...state, page, search, filters, update, reload: load };
}
