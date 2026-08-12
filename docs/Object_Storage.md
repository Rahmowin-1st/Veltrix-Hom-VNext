# Object Storage

`StorageAdapter` exposes put/open/delete/head/signed-read behavior. Production/staging uses S3-compatible storage; CI uses real MinIO. Database rows store provider, object key, hash, size, MIME, ETag/version metadata. User filenames never become trusted filesystem paths.
