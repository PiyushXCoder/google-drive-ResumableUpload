# google-drive-ResumableUpload
A helpful java library/class for uploading large files in chunks on google drive.

## Example usage for uploading a file

Here's an example for uploading a java.io.File to google drive.

```java
// Make sure we've already got Google OAuth Credentials object `credential` and
// java.io.File object `file` for the desired file to be uploaded.

// Now create the metadata google drive file with the name, mime-type and size
File fmeta = new File();
fmeta.setName("My Report");
fmeta.setMimeType("application/vnd.google-apps.spreadsheet");
fmeta.setSize(file.length()); // `file` is the object for the desired java.io.File

// Now, use uploadFile() method to start uploading the file in multi-part requests.
ResumableUpload.uploadFile(credential, fmeta, file);
```
## How this works

 - At, first we use the `requestUploadUrl()` to create a new request to the google drive api for
our requirement of uploading a file. This method returns the url retured by the google api, which we'll be
using to upload the file data.
 - Then we can use `uploadFilePacket()` to upload a part of a file or `uploadStringPacket()` to upload
 a part of any string as a file data. This step can repeated for each part of part of the file.
 
