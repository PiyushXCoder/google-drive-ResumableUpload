
import com.google.api.client.auth.oauth2.Credential;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * A helpful java library/class for uploading large files in chunks on google drive.
 *
 * @author Piyush M.
 */
public class ResumableUpload
{
    public static final int CHUNK_LIMIT = 262144; // = (256*1024)
    
    public static final int OK          = 200;
    public static final int CREATED     = 201;
    public static final int INCOMPLETE  = 308;
    
  
    /**
     * This function returns url to which file is to be uploaded
     * @param credential google credential for AccessToken
     * @param jsonStructure  It will be used to get structure of file it should contain
     *              1) MimeType of file
     *              2) Size of file
     *              3) Name of file
     * @return SessionUri
     * @throws MalformedURLException
     * @throws IOException 
     */
    public static String requestUploadUrl(Credential credential, com.google.api.services.drive.model.File jsonStructure) throws MalformedURLException, IOException
    {
        URL url = new URL("https://www.googleapis.com/upload/drive/v3/files"+((jsonStructure.getId() != null)?"/"+jsonStructure.getId():"")+"?uploadType=resumable");
        HttpURLConnection req = (HttpURLConnection) url.openConnection();
        req.setRequestMethod("POST");
        req.setDoInput(true);
        req.setDoOutput(true);
        req.setRequestProperty("Authorization", "Bearer " + credential.getAccessToken());
        req.setRequestProperty("X-Upload-Content-Type", jsonStructure.getMimeType());
        req.setRequestProperty("X-Upload-Content-Length", String.valueOf(jsonStructure.getSize()));
        req.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        String body = "{ \"name\": \""+jsonStructure.getName()+"\""+((jsonStructure.getParents() != null)?", \"parents\":[\""+jsonStructure.getParents().get(0)+"\"]":"")+" }";
        req.setRequestProperty("Content-Length", String.format(Locale.ENGLISH, "%d", body.getBytes().length));
        try (OutputStream outputStream = req.getOutputStream()) {
            outputStream.write(body.getBytes());
        }
        req.connect();

        String sessionUri = null;

        if (req.getResponseCode() == HttpURLConnection.HTTP_OK) {
            sessionUri = req.getHeaderField("location");
        }
                
        return sessionUri; 
    }
    
    /**
     * Uploads String packet 
     * @param sessionUri Last Session Url
     * @param jsonStructure It will be used to get structure of file it should contain
     *              1) MimeType of file
     *              2) Size of file
     *              3) Name of file
     * @param packet Text to upload
     * @param chunkStart offset of start of chunk 
     * @param uploadBytes length of packet must be greater than CHUNK_LIMIT except last packet
     * @return Response Code
     * @throws MalformedURLException
     * @throws IOException 
     */
    public static int uploadStringPacket(String sessionUri, com.google.api.services.drive.model.File jsonStructure, String packet, long chunkStart, long uploadBytes) throws MalformedURLException, IOException
    {
        URL url = new URL(sessionUri);
        HttpURLConnection req = (HttpURLConnection) url.openConnection();
        
        req.setRequestMethod("PUT");
        req.setDoOutput(true);
        req.setDoInput(true);
        req.setConnectTimeout(10000);
        
        req.setRequestProperty("Content-Type", jsonStructure.getMimeType());
        req.setRequestProperty("Content-Length", String.valueOf(uploadBytes));
        req.setRequestProperty("Content-Range", "bytes " + chunkStart + "-" + (chunkStart + uploadBytes -1) + "/" + jsonStructure.getSize());
        
        byte[] buffer = packet.substring((int)chunkStart, (int)(chunkStart + uploadBytes)).getBytes();
                
        try (OutputStream outputStream = req.getOutputStream()) {
            outputStream.write(buffer);
        }
        
        req.connect();
        
        return req.getResponseCode();
    }
    
    /**
     * Upload java.io.File packet
     * @param sessionUri Last Session Url
     * @param jsonStructure It will be used to get structure of file it should contain
     *              1) MimeType of file
     *              2) Size of file
     *              3) Name of file
     * @param file File to upload
     * @param chunkStart offset of start of chunk 
     * @param uploadBytes length of packet must be greater than CHUNK_LIMIT except last packet
     * @return Response Code
     * @throws MalformedURLException
     * @throws IOException 
     */    
    public static int uploadFilePacket(String sessionUri, com.google.api.services.drive.model.File jsonStructure, java.io.File file, long chunkStart, long uploadBytes) throws MalformedURLException, IOException
    {
        URL url1 = new URL(sessionUri);
        HttpURLConnection req = (HttpURLConnection) url1.openConnection();
        
        req.setRequestMethod("PUT");
        req.setDoOutput(true);
        req.setDoInput(true);
        req.setConnectTimeout(10000);
        
        req.setRequestProperty("Content-Type", jsonStructure.getMimeType());
        req.setRequestProperty("Content-Length", String.valueOf(uploadBytes));
        req.setRequestProperty("Content-Range", "bytes " + chunkStart + "-" + (chunkStart + uploadBytes -1) + "/" + jsonStructure.getSize());
        
        try (OutputStream outstream = req.getOutputStream()) {
            byte[] buffer = new byte[(int) uploadBytes];
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                fileInputStream.getChannel().position(chunkStart);
                if (fileInputStream.read(buffer, 0, (int) uploadBytes) != -1)
                    outstream.write(buffer);
            }
        }
        
        req.connect();
                
        return req.getResponseCode();
    }
    
    /**
     * Upload File
     * Upload java.io.File packet
     * @param credential google credential for AccessToken
     * @param jsonStructure It will be used to get structure of file it should contain
     *              1) MimeType of file
     *              2) Size of file
     *              3) Name of file
     * @param file File to upload
     * @throws IOException 
     */
    public static void uploadFile(Credential credential, com.google.api.services.drive.model.File jsonStructure, java.io.File file) throws IOException, UploadFileException
    {
        String sessionUrl = requestUploadUrl(credential, jsonStructure);
        
        for(long i = 1, j = CHUNK_LIMIT;i <= jsonStructure.getSize();i+=CHUNK_LIMIT)
        {
            if(i+CHUNK_LIMIT >= jsonStructure.getSize())
            {
                j = jsonStructure.getSize() - i + 1;
            }
            int responseCode = uploadFilePacket(sessionUrl, jsonStructure, file, i-1, j);
            if(!(responseCode == OK || responseCode == CREATED || responseCode == INCOMPLETE)) throw new UploadFileException(responseCode);
        }
    }
    
    /**
     * 
     * Upload String
     * @param credential google credential for AccessToken
     * @param jsonStructure It will be used to get structure of file it should contain
     *              1) MimeType of file
     *              2) Size of file
     *              3) Name of file
     * @param text Text to upload
     * @throws IOException 
     */
    public void uploadString(Credential credential, com.google.api.services.drive.model.File jsonStructure, String text) throws IOException, UploadFileException
    {
        String sessionUrl = requestUploadUrl(credential, jsonStructure);
        
        for(long i = 1, j = CHUNK_LIMIT;i <= jsonStructure.getSize();i+=CHUNK_LIMIT)
        {
            if(i+CHUNK_LIMIT >= jsonStructure.getSize())
            {
                j = jsonStructure.getSize() - i + 1;
            }
            int responseCode = uploadStringPacket(sessionUrl, jsonStructure, text, i-1, j);
            if(!(responseCode == OK || responseCode == CREATED || responseCode == INCOMPLETE)) throw new UploadFileException(responseCode);
        }
    }
    
    /**
     * Exception thrown when there is a problem while uploading file 
     */
    public static class UploadFileException extends Exception
    {
        public UploadFileException()
        {
            super("Unable to upload file!");
        }
        
        public UploadFileException(int responsecode)
        {
            super("Unable to upload file! ResponseCode: "+responsecode);
        }
        
        public UploadFileException(String msg, int responsecode)
        {
            super(msg+responsecode);
        }
    }
}
