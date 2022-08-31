package org.openrefine.extensions.commons.importer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.refine.expr.EvalError;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/*
 * This class takes an existing iterator over file records, and enriches
 * the file records with the list of categories they belong to
 *
 * @param apiUrl
 * @param iteratorFileRecords
 */
public class RelatedCategoryFetcher implements Iterator<FileRecord> {
    public static int API_TITLES_LIMIT = 20;
    Iterator<FileRecord> iteratorFileRecords;
    List <FileRecord> fileRecordOriginal = new ArrayList<>();
    List<FileRecord> fileRecordNew = new ArrayList<>();
    List<List<String>> toCategoriesColumn;
    String apiUrl;
    int fileRecordNewIndex = 0;

    public RelatedCategoryFetcher(String apiUrl, Iterator<FileRecord> iteratorFileRecords) {
        this.apiUrl = apiUrl;
        this.iteratorFileRecords = iteratorFileRecords;
    }

    /*
     * API call for fetching the related categories in batches of up to 20 titles
     * @param list of file records
     * @return list of related categories listed per file
     */
    public List<List<String>> getRelatedCategories(List <FileRecord> fileRecordOriginal) throws IOException {

        String titles = fileRecordOriginal.get(0).fileName;
        int titlesIndex =1;
        if (titlesIndex < fileRecordOriginal.size()) {
            titles += "|" + fileRecordOriginal.get(titlesIndex++).fileName;
        }
        OkHttpClient client = new OkHttpClient.Builder().build();
        HttpUrl urlRelatedCategories = HttpUrl.parse(apiUrl).newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("prop", "categories")
                .addQueryParameter("titles", titles)
                .addQueryParameter("format", "json").build();
        Request request = new Request.Builder().url(urlRelatedCategories).build();
        Response response = client.newCall(request).execute();
        JsonNode jsonNode = new ObjectMapper().readTree(response.body().string());
        List<JsonNode> relatedCategories = new ArrayList<>();
        toCategoriesColumn = new ArrayList<>();
        for (int i = 0; i < fileRecordOriginal.size(); i++) {
            relatedCategories.add(jsonNode.path("query").path("pages").path(fileRecordOriginal.get(i).pageId).path("categories"));
            List<String> categoriesPerFile = new ArrayList<>();
            for (int j = 0; j < relatedCategories.get(i).size(); j++) {
                categoriesPerFile.add(relatedCategories.get(i).get(j).findValue("title").asText());
            }
            toCategoriesColumn.add(categoriesPerFile);
        }

        return toCategoriesColumn;
    }

    /*
     * Returns {@code true} if the iteration has more elements for
     * which to fetch related categories.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Override
    public boolean hasNext() {
        return iteratorFileRecords.hasNext();
    }

    /*
     * This method iterates over each of the categories related to a file
     * and stores them as a list in the relatedCategories parameter of
     * each file record
     *
     * @return an instance of the FileRecord updated to include its related categories
     */
    @Override
    public FileRecord next() {

        if (fileRecordNewIndex <= 0) {
            int filesIndex = 0;
            if (filesIndex < API_TITLES_LIMIT) {
                while (iteratorFileRecords.hasNext()) {
                    fileRecordOriginal.add(iteratorFileRecords.next());
                    filesIndex++;
                }
            }
            try {
                getRelatedCategories(fileRecordOriginal);
            } catch (IOException e) {
                // FIXME
                e.printStackTrace();
            }
            for (int i = 0; i < fileRecordOriginal.size(); i++) {
                fileRecordNew.add(new FileRecord(fileRecordOriginal.get(i).fileName, fileRecordOriginal.get(i).pageId,
                        toCategoriesColumn.get(i), null));
                fileRecordNewIndex++;
            }
            if (iteratorFileRecords.hasNext()) {
                filesIndex = 0;
            }
            return fileRecordNew.get(--fileRecordNewIndex);
        } else {
            return fileRecordNew.get(--fileRecordNewIndex);
        }
    }

}
