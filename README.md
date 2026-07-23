# NevTan Drive API

Spring Boot 3 / Java 17 backend for NevTan Drive. The database stores metadata
only. File bytes are handled through `CloudStorageService`.

Local development uses H2 and `LocalMockCloudStorageService`, which writes to
`./local-drive-storage/{authenticated-email}/`.

## Local setup

Prerequisites:

- Java 17
- Maven 3.9+

Run:

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8081`.

Run with the stage MySQL profile:

```bash
SPRING_PROFILES_ACTIVE=stage mvn spring-boot:run
```

Run with the production MySQL profile:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/nevtan-drive-api-0.0.1-SNAPSHOT.jar
```

Identity is owned by NevTan SSO. Drive never receives a password: the client
signs in against the SSO, then exchanges that token for a Drive session — the
same flow the CylonCloud API uses. Users are auto-provisioned into
`drive_users` on first sign-in; sign-up and email verification happen in the
SSO.

Step 1 — authenticate against the SSO:

```bash
curl -X POST "https://sso.nevtan.com/auth/signin" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"..."}'
# -> { "data": { "accessToken": "<sso token>", ... } }
```

Step 2 — exchange it for a Drive session:

```bash
curl -X POST "http://localhost:8081/api/drive/auth/sso" \
  -H "Content-Type: application/json" \
  -d '{"ssoToken":"<sso token>"}'
# -> { "accessToken": "<drive token>", "refreshToken": "...", "expiresIn": 36000, "user": {...} }
```

Every other endpoint takes the Drive token:

```text
Authorization: Bearer <drive access token>
```

| Endpoint | Purpose |
|---|---|
| `POST /api/drive/auth/sso` | Exchange an SSO token for a Drive session |
| `POST /api/drive/auth/refresh` | Rotate the refresh token, get a new session |
| `POST /api/drive/auth/logout` | Revoke the refresh token |
| `GET /api/drive/auth/me` | The signed-in user's profile |

An SSO token is accepted only at the exchange endpoint, never as a session
token. The current user comes from the validated token, never from a query
parameter or request body. Public share downloads do not require auth.

Example upload:

```bash
curl -X POST \
  "http://localhost:8081/api/drive/upload" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "file=@example.pdf"
```

## Limits and file validation

- Per-user storage quota: `1073741824` bytes by default.
- Per-file upload limit: `104857600` bytes by default.
- Blocked extensions are matched case-insensitively.
- Filenames are normalized, stripped of path components/control characters,
  cleaned of unsafe filesystem characters, and limited to 255 characters.
- The submitted MIME type is preserved in metadata. Missing MIME types use
  `application/octet-stream`.
- File bytes are never stored in the database.

## Pagination

File listing and search accept:

- `page`: zero-based page index; default `0`
- `size`: page size from `1` to `100`; default `20`

Both return Spring `Page<DriveFileResponse>` JSON, including `content`,
`totalElements`, `totalPages`, `number`, and `size`.

## Endpoints

Files:

- `POST /api/drive/upload?folderId=`
- `GET /api/drive/files?folderId=&page=0&size=20`
- `GET /api/drive/recent?page=0&size=20`
- `GET /api/drive/starred?page=0&size=20`
- `GET /api/drive/search?q=&page=0&size=20`
- `GET /api/drive/download/{fileId}`
- `GET /api/drive/preview/{fileId}`
- `GET /api/drive/files/{fileId}/details`
- `PATCH /api/drive/files/{fileId}/rename`
- `PATCH /api/drive/files/{fileId}/move`
- `PATCH /api/drive/files/{fileId}/star`
- `PATCH /api/drive/files/{fileId}/unstar`
- `DELETE /api/drive/files/{fileId}`
- `POST /api/drive/files/{fileId}/restore`
- `DELETE /api/drive/files/{fileId}/permanent`
- `GET /api/drive/storage`

Folders:

- `POST /api/drive/folders`
- `GET /api/drive/folders?parentFolderId=`
- `PATCH /api/drive/folders/{folderId}`
- `DELETE /api/drive/folders/{folderId}`
- `POST /api/drive/folders/{folderId}/restore`
- `DELETE /api/drive/folders/{folderId}/permanent`
- `GET /api/drive/trash`

Share links:

- `POST /api/drive/share/{fileId}`
- `GET /api/drive/share/file/{fileId}`
- `PATCH /api/drive/share/{shareId}`
- `GET /api/drive/share/{token}`
- `DELETE /api/drive/share/{shareId}`

Permissions:

- `POST /api/drive/permissions/{fileId}`
- `GET /api/drive/permissions/{fileId}`
- `DELETE /api/drive/permissions/{permissionId}`
- `GET /api/drive/shared-with-me`

## Tests

Run all tests:

```bash
mvn test
```

Build the executable JAR and rerun tests:

```bash
mvn package
```

## Environment variables

Drive:

| Variable | Default |
|---|---|
| `SERVER_PORT` | `8081` |
| `DRIVE_STORAGE_LIMIT_BYTES` | `1073741824` |
| `DRIVE_MAX_UPLOAD_SIZE_BYTES` | `104857600` |
| `DRIVE_MAX_REQUEST_SIZE_BYTES` | `105906176` |
| `DRIVE_BLOCKED_EXTENSIONS` | `.exe,.bat,.cmd,.sh,.js,.jar` |
| `DRIVE_LOCAL_STORAGE_ROOT` | `./local-drive-storage` |
| `DRIVE_SHARE_BASE_URL` | `http://localhost:5173/drive/share` |
| `SSO_INTROSPECT_URL` | `https://sso.nevtan.com/oauth/introspect` |
| `JWT_SECRET` | development-only default; **must** be overridden in deployments |
| `JWT_EXPIRATION_MS` | `36000000` (10 hours) |
| `JWT_REFRESH_EXPIRATION_MS` | `2592000000` (30 days) |
| `DRIVE_CORS_ALLOWED_ORIGINS` | localhost dev origins plus the deployed Drive origins |

MySQL stage/production:

| Variable | Stage default | Production default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `stage` | `prod` |
| `MYSQL_URL` | `jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?...` | `jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?...` |
| `MYSQL_HOST` | `localhost` | `localhost` |
| `MYSQL_PORT` | `3306` | `3306` |
| `MYSQL_DATABASE` | `nevtandrive_stage` | `nevtandrive` |
| `MYSQL_USERNAME` | `nevtandrive_stage` | `nevtandrive` |
| `MYSQL_PASSWORD` | empty | empty |
| `MYSQL_DDL_AUTO` | `update` | `validate` |

For stage/prod, set `DRIVE_SHARE_BASE_URL` to the deployed frontend share URL,
and set `JWT_SECRET` to a strong Base64 value of at least 256 bits. The prod
profile intentionally provides no default for it, so the application fails to
start rather than signing sessions with a publicly known key.

NevTan Cloud:

| Variable | Default |
|---|---|
| `NEVTAN_CLOUD_ENABLED` | `false` |
| `NEVTAN_CLOUD_BASE_URL` | empty |
| `NEVTAN_CLOUD_API_KEY` | empty |
| `NEVTAN_CLOUD_BUCKET` | empty |
| `NEVTAN_CLOUD_UPLOAD_ENDPOINT` | empty |
| `NEVTAN_CLOUD_DOWNLOAD_ENDPOINT` | empty |
| `NEVTAN_CLOUD_DELETE_ENDPOINT` | empty |
| `NEVTAN_CLOUD_SIGNED_URL_ENDPOINT` | empty |

When `NEVTAN_CLOUD_ENABLED=false`, local mock storage is selected. When it is
`true`, every NevTan Cloud property is validated at startup.

Secrets such as `NEVTAN_CLOUD_API_KEY` are not included in application logs or
exception messages.

## NevTan Cloud TODOs

The adapter intentionally does not guess the cloud protocol. Confirm these
items with the NevTan Cloud team before implementing HTTP requests:

1. Authentication mechanism and header/signing rules.
2. Upload endpoint, HTTP method, and streaming request format.
3. Download endpoint and whether it streams or redirects.
4. Delete endpoint, HTTP method, and idempotency behavior.
5. Returned object-key field and response schema.
6. Signed URL support, expiry controls, and response field.
7. Maximum accepted upload size.

`NevTanCloudStorageService` contains TODO boundaries for each operation.

## Consistency model

Uploads stream to storage first and then flush metadata. If metadata persistence
fails, the uploaded object is deleted as compensation.

Deletes flush the metadata soft-delete before deleting the storage object. If
storage deletion fails, the exception causes the database transaction to roll
back, leaving the file active for retry. This avoids reporting a successful
delete when the physical object still exists.
