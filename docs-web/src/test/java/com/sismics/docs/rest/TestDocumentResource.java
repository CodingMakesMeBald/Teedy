package com.sismics.docs.rest;

import java.io.InputStream;
import java.util.Date;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.joda.time.format.DateTimeFormat;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import com.sismics.docs.core.util.DirectoryUtil;
import com.sismics.util.filter.TokenBasedSecurityFilter;
import com.sismics.util.mime.MimeType;
import com.sismics.util.mime.MimeTypeUtil;

/**
 * Exhaustive test of the document resource.
 * 
 * @author bgamard
 */
public class TestDocumentResource extends BaseJerseyTest {
    /**
     * Test the document resource.
     * 
     * @throws Exception 
     */
    @Test
    public void testDocumentResource() throws Exception {
        // Login document1
        clientUtil.createUser("document1");
        String document1Token = clientUtil.login("document1");
        
        // Login document3
        clientUtil.createUser("document3");
        String document3Token = clientUtil.login("document3");
        
        // Create a tag
        JsonObject json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .put(Entity.form(new Form()
                        .param("name", "SuperTag")
                        .param("color", "#ffff00")), JsonObject.class);
        String tag1Id = json.getString("id");
        Assert.assertNotNull(tag1Id);

        // Create a document
        long create1Date = new Date().getTime();
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .put(Entity.form(new Form()
                        .param("title", "My super title document 1")
                        .param("description", "My super description for document 1")
                        .param("tags", tag1Id)
                        .param("language", "eng")
                        .param("create_date", Long.toString(create1Date))), JsonObject.class);
        String document1Id = json.getString("id");
        Assert.assertNotNull(document1Id);
        
        // Add a file
        String file1Id = null;
        try (InputStream is = Resources.getResource("file/Einstein-Roosevelt-letter.png").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "Einstein-Roosevelt-letter.png");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                        .put(Entity.entity(multiPart.field("id", document1Id).bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file1Id = json.getString("id");
                Assert.assertNotNull(file1Id);
            }
        }
        
        // Share this document
        json = target().path("/share").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .put(Entity.form(new Form().param("id", document1Id)), JsonObject.class);
        
        // List all documents
        json = target().path("/document/list")
                .queryParam("sort_column", 3)
                .queryParam("asc", false)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .get(JsonObject.class);
        JsonArray documents = json.getJsonArray("documents");
        JsonArray tags = documents.getJsonObject(0).getJsonArray("tags");
        Assert.assertTrue(documents.size() == 1);
        Assert.assertEquals(document1Id, documents.getJsonObject(0).getString("id"));
        Assert.assertEquals("eng", documents.getJsonObject(0).getString("language"));
        Assert.assertEquals(1, documents.getJsonObject(0).getInt("file_count"));
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tag1Id, tags.getJsonObject(0).getString("id"));
        Assert.assertEquals("SuperTag", tags.getJsonObject(0).getString("name"));
        Assert.assertEquals("#ffff00", tags.getJsonObject(0).getString("color"));
        
        // List all documents from document3
        json = target().path("/document/list")
                .queryParam("sort_column", 3)
                .queryParam("asc", false)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document3Token)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertTrue(documents.size() == 0);
        
        // Create a document with document3
        long create3Date = new Date().getTime();
        json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document3Token)
                .put(Entity.form(new Form()
                        .param("title", "My super title document 1")
                        .param("description", "My super description for document 1")
                        .param("language", "eng")
                        .param("create_date", Long.toString(create3Date))), JsonObject.class);
        String document3Id = json.getString("id");
        Assert.assertNotNull(document3Id);
        
        // Add a file
        String file3Id = null;
        try (InputStream is = Resources.getResource("file/Einstein-Roosevelt-letter.png").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "Einstein-Roosevelt-letter.png");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document3Token)
                        .put(Entity.entity(multiPart.field("id", document3Id).bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file3Id = json.getString("id");
                Assert.assertNotNull(file3Id);
            }
        }
        
        // List all documents from document3
        json = target().path("/document/list")
                .queryParam("sort_column", 3)
                .queryParam("asc", false)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document3Token)
                .get(JsonObject.class);
        documents = json.getJsonArray("documents");
        Assert.assertTrue(documents.size() == 1);
        
        // Search documents
        Assert.assertEquals(1, searchDocuments("full:uranium full:einstein", document1Token));
        Assert.assertEquals(1, searchDocuments("full:title", document1Token));
        Assert.assertEquals(1, searchDocuments("title", document1Token));
        Assert.assertEquals(1, searchDocuments("super description", document1Token));
        Assert.assertEquals(1, searchDocuments("at:" + DateTimeFormat.forPattern("yyyy").print(new Date().getTime()), document1Token));
        Assert.assertEquals(1, searchDocuments("at:" + DateTimeFormat.forPattern("yyyy-MM").print(new Date().getTime()), document1Token));
        Assert.assertEquals(1, searchDocuments("at:" + DateTimeFormat.forPattern("yyyy-MM-dd").print(new Date().getTime()), document1Token));
        Assert.assertEquals(1, searchDocuments("after:2010 before:2040-08", document1Token));
        Assert.assertEquals(1, searchDocuments("tag:super", document1Token));
        Assert.assertEquals(1, searchDocuments("shared:yes", document1Token));
        Assert.assertEquals(1, searchDocuments("lang:eng", document1Token));
        Assert.assertEquals(1, searchDocuments("after:2010 before:2040-08 tag:super shared:yes lang:eng title description full:uranium", document1Token));

        // Search documents (nothing)
        Assert.assertEquals(0, searchDocuments("random", document1Token));
        Assert.assertEquals(0, searchDocuments("full:random", document1Token));
        Assert.assertEquals(0, searchDocuments("after:2010 before:2011-05-20", document1Token));
        Assert.assertEquals(0, searchDocuments("at:2040-05-35", document1Token));
        Assert.assertEquals(0, searchDocuments("after:2010-18 before:2040-05-38", document1Token));
        Assert.assertEquals(0, searchDocuments("after:2010-18", document1Token));
        Assert.assertEquals(0, searchDocuments("before:2040-05-38", document1Token));
        Assert.assertEquals(0, searchDocuments("tag:Nop", document1Token));
        Assert.assertEquals(0, searchDocuments("lang:fra", document1Token));

        // Get a document
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .get(JsonObject.class);
        Assert.assertEquals(document1Id, json.getString("id"));
        Assert.assertEquals("document1", json.getString("creator"));
        Assert.assertEquals(1, json.getInt("file_count"));
        Assert.assertEquals(true, json.getBoolean("shared"));
        Assert.assertEquals("My super title document 1", json.getString("title"));
        Assert.assertEquals("My super description for document 1", json.getString("description"));
        Assert.assertEquals("eng", json.getString("language"));
        Assert.assertEquals(create1Date, json.getJsonNumber("create_date").longValue());
        tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tag1Id, tags.getJsonObject(0).getString("id"));
        
        // Create a tag
        json = target().path("/tag").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .put(Entity.form(new Form().param("name", "SuperTag2").param("color", "#00ffff")), JsonObject.class);
        String tag2Id = json.getString("id");
        Assert.assertNotNull(tag1Id);
        
        // Update a document
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .post(Entity.form(new Form()
                        .param("title", "My new super document 1")
                        .param("description", "My new super description for document 1")
                        .param("tags", tag2Id)), JsonObject.class);
        Assert.assertEquals(document1Id, json.getString("id"));
        
        // Search documents by query
        json = target().path("/document/list")
                .queryParam("search", "super")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .get(JsonObject.class);
        
        // Get a document
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .get(JsonObject.class);
        Assert.assertTrue(json.getString("title").contains("new"));
        Assert.assertTrue(json.getString("description").contains("new"));
        tags = json.getJsonArray("tags");
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tag2Id, tags.getJsonObject(0).getString("id"));
        
        // Deletes a document
        json = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .delete(JsonObject.class);
        Assert.assertEquals("ok", json.getString("status"));
        
        // Check that the associated files are deleted from FS
        java.io.File storedFile = DirectoryUtil.getStorageDirectory().resolve(file1Id).toFile();
        java.io.File webFile = DirectoryUtil.getStorageDirectory().resolve(file1Id + "_web").toFile();
        java.io.File thumbnailFile = DirectoryUtil.getStorageDirectory().resolve(file1Id + "_thumb").toFile();
        Assert.assertFalse(storedFile.exists());
        Assert.assertFalse(webFile.exists());
        Assert.assertFalse(thumbnailFile.exists());
        
        // Get a document (KO)
        Response response = target().path("/document/" + document1Id).request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document1Token)
                .get();
        Assert.assertEquals(Status.NOT_FOUND, Status.fromStatusCode(response.getStatus()));
    }
    
    /**
     * Search documents and returns the number found.
     * 
     * @param query Search query
     * @param token Authentication token
     * @return Number of documents found
     * @throws Exception
     */
    private int searchDocuments(String query, String token) throws Exception {
        JsonObject json = target().path("/document/list")
                .queryParam("search", query)
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, token)
                .get(JsonObject.class);
        return json.getJsonArray("documents").size();
    }
    
    /**
     * Test PDF extraction.
     * 
     * @throws Exception 
     */
    @Test
    public void testPdfExtraction() throws Exception {
        // Login document2
        clientUtil.createUser("document2");
        String document2Token = clientUtil.login("document2");

        // Create a document
        long create1Date = new Date().getTime();
        JsonObject json = target().path("/document").request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document2Token)
                .put(Entity.form(new Form()
                        .param("title", "My super title document 1")
                        .param("description", "My super description for document 1")
                        .param("language", "eng")
                        .param("create_date", Long.toString(create1Date))), JsonObject.class);
        String document1Id = json.getString("id");
        Assert.assertNotNull(document1Id);
        
        // Add a PDF file
        String file1Id = null;
        try (InputStream is = Resources.getResource("file/wikipedia.pdf").openStream()) {
            StreamDataBodyPart streamDataBodyPart = new StreamDataBodyPart("file", is, "wikipedia.pdf");
            try (FormDataMultiPart multiPart = new FormDataMultiPart()) {
                json = target()
                        .register(MultiPartFeature.class)
                        .path("/file").request()
                        .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document2Token)
                        .put(Entity.entity(multiPart.field("id", document1Id).bodyPart(streamDataBodyPart),
                                MediaType.MULTIPART_FORM_DATA_TYPE), JsonObject.class);
                file1Id = json.getString("id");
                Assert.assertNotNull(file1Id);
            }
        }
        
        // Search documents by query in full content
        json = target().path("/document/list")
                .queryParam("search", "full:vrandecic")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document2Token)
                .get(JsonObject.class);
        Assert.assertTrue(json.getJsonArray("documents").size() == 1);
        
        // Get the file thumbnail data
        Response response = target().path("/file/" + file1Id + "/data")
                .queryParam("size", "thumb")
                .request()
                .cookie(TokenBasedSecurityFilter.COOKIE_NAME, document2Token)
                .get();
        InputStream is = (InputStream) response.getEntity();
        byte[] fileBytes = ByteStreams.toByteArray(is);
        Assert.assertTrue(fileBytes.length > 0); // Images rendered from PDF differ in size from OS to OS due to font issues
        Assert.assertEquals(MimeType.IMAGE_JPEG, MimeTypeUtil.guessMimeType(fileBytes));
    }
}