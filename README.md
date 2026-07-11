# NevTan Drive API

Spring Boot 3 / Java 17 backend for NevTan Drive. The database stores metadata
only. File bytes are handled through `CloudStorageService`.

Local development uses H2 and `LocalMockCloudStorageService`, which writes to
`./local-drive-storage/{userEmail}/`.

## Local setup

Prerequisites:

- Java 17
- Maven 3.9+

Run:

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:18080`.

Until authentication is integrated, private endpoints require a `userEmail`
request parameter. Public share downloads do not require it.

Example upload:

```bash
curl -X POST \
  "http://localhost:18080/api/drive/upload?userEmail=user@example.com" \
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

- `POST /api/drive/upload?userEmail=&folderId=`
- `GET /api/drive/files?userEmail=&folderId=&page=0&size=20`
- `GET /api/drive/recent?userEmail=&page=0&size=20`
- `GET /api/drive/starred?userEmail=&page=0&size=20`
- `GET /api/drive/search?userEmail=&q=&page=0&size=20`
- `GET /api/drive/download/{fileId}?userEmail=`
- `GET /api/drive/preview/{fileId}?userEmail=`
- `GET /api/drive/files/{fileId}/details?userEmail=`
- `PATCH /api/drive/files/{fileId}/rename?userEmail=`
- `PATCH /api/drive/files/{fileId}/move?userEmail=`
- `PATCH /api/drive/files/{fileId}/star?userEmail=`
- `PATCH /api/drive/files/{fileId}/unstar?userEmail=`
- `DELETE /api/drive/files/{fileId}?userEmail=`
- `POST /api/drive/files/{fileId}/restore?userEmail=`
- `DELETE /api/drive/files/{fileId}/permanent?userEmail=`
- `GET /api/drive/storage?userEmail=`

Folders:

- `POST /api/drive/folders?userEmail=`
- `GET /api/drive/folders?userEmail=&parentFolderId=`
- `PATCH /api/drive/folders/{folderId}?userEmail=`
- `DELETE /api/drive/folders/{folderId}?userEmail=`
- `POST /api/drive/folders/{folderId}/restore?userEmail=`
- `DELETE /api/drive/folders/{folderId}/permanent?userEmail=`
- `GET /api/drive/trash?userEmail=`

Share links:

- `POST /api/drive/share/{fileId}?userEmail=`
- `GET /api/drive/share/file/{fileId}?userEmail=`
- `PATCH /api/drive/share/{shareId}?userEmail=`
- `GET /api/drive/share/{token}`
- `DELETE /api/drive/share/{shareId}?userEmail=`

Permissions:

- `POST /api/drive/permissions/{fileId}?userEmail=`
- `GET /api/drive/permissions/{fileId}?userEmail=`
- `DELETE /api/drive/permissions/{permissionId}?userEmail=`
- `GET /api/drive/shared-with-me?userEmail=`

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
| `SERVER_PORT` | `18080` |
| `DRIVE_STORAGE_LIMIT_BYTES` | `1073741824` |
| `DRIVE_MAX_UPLOAD_SIZE_BYTES` | `104857600` |
| `DRIVE_MAX_REQUEST_SIZE_BYTES` | `105906176` |
| `DRIVE_BLOCKED_EXTENSIONS` | `.exe,.bat,.cmd,.sh,.js,.jar` |
| `DRIVE_LOCAL_STORAGE_ROOT` | `./local-drive-storage` |
| `DRIVE_SHARE_BASE_URL` | `http://localhost:5173/drive/share` |

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
