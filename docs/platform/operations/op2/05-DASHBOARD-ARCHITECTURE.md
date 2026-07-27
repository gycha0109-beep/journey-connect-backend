# Dashboard Architecture

The application publishes bounded Micrometer metrics in the `rca2.` namespace. A future external metrics backend and dashboard may consume only the contract inventory in `dashboard-inventory.json`.

No raw identity, token, full endpoint URL or unbounded error text is permitted in dashboard dimensions.
