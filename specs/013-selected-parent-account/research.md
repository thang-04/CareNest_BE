# Research — 013
Constitution checked unchanged. Staff account scope remains existing policy; child contacts and pickup remain independent of recipient. Existing CSV import supports one guardian only and real BE import/pending/activate are missing, so implement these endpoints instead of only UI. Keep multipart portrait part indexes aligned with the full guardian array.

Existing POI dependency is reused for XLS; XLSX parsing must bound expanded ZIP bytes, rows/columns and disable external XML entities. UTF-8 CSV header aliases are matched longest first to distinguish optional guardian groups 2/3. Existing account-link endpoints require selected-guardian identity to prevent bypass. Shared account lookup is reuse, not a new account or password reset.

Implemented account identity CAS accepts null/same citizen ID; zero-row claim rejects and rolls back the link transaction. Form, modern activation and legacy callbacks share this guard. Selected projection uses persisted user_id. Batch CHD-04 uses same name, birth date and at least one shared guardian citizen ID.
