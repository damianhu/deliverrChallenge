package com.example.deliverr.project.service;

import com.example.deliverr.project.model.DeliverrItem;
import com.example.deliverr.project.model.WishItem;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Service
public class WishService {
    private final static String getFulfillUrl = "https://merchant.wish.com/api/v2/order/get-fulfill";
    private final static String getProductUrl = "https://merchant.wish.com/api/v2/product";
    private final static String getVariantUrl = "https://merchant.wish.com/api/v2/variant";
    private final static String postVariantUrl = "https://merchant.wish.com/api/v2/variant/update";
    private final static int MAX_TOTAL = 100;
    private final static int MAX_PERROUTE = 50;

    private CloseableHttpClient httpClient;

    public WishService(){
        httpClient = getHttpClient();
    }

    public List<WishItem> getWishProducts() throws IOException, URISyntaxException {
        List<WishItem> res = new ArrayList<>();
        HttpGet httpGet = new HttpGet(getFulfillUrl);
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity httpEntity = response.getEntity();

        if(httpEntity != null){
            String jsonStr = EntityUtils.toString(httpEntity);
            JSONObject jsonObject = JSONObject.fromObject(jsonStr);

            JSONArray jsonArray = jsonObject.getJSONArray("data");
            for(int i=0; i<jsonArray.size(); i++){
                JSONObject jsonObject2 = (JSONObject) jsonArray.get(i);
                JSONObject order = (JSONObject) jsonObject2.get("Order");
                String sku = order.getString("sku");
                String productId = order.getString("product_id");
                int quantity = order.getInt("quantity");

                String description = getProductDescription(productId);
                int inventory = getVariantInventory(sku);
                postVariantInventory(sku, inventory, quantity);

                //store wishItem
                WishItem wItem = new WishItem();
                wItem.setDescription(description);
                wItem.setProductId(productId);
                wItem.setSku(sku);
                wItem.setInventory(inventory);
                res.add(wItem);
            }
        }
        return res;
    }

    private String getProductDescription(String productId) throws URISyntaxException, IOException {
            HttpGet httpGet = new HttpGet(getProductUrl);
            URI uri = new URIBuilder(httpGet.getURI())
                    .addParameter("id", productId)
                    .build();
            httpGet.setURI(uri);
            HttpResponse httpResponse = httpClient.execute(httpGet);
            String json = EntityUtils.toString(httpResponse.getEntity());
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONObject data = (JSONObject) jsonObject.get("data");
            JSONObject product = (JSONObject) data.get("Product");
            String description = product.getString("description");
            return description;
    }

    private int getVariantInventory(String sku) throws IOException, URISyntaxException {
            HttpGet httpGet = new HttpGet(getVariantUrl);
            URI uri = new URIBuilder(httpGet.getURI())
                    .addParameter("sku", sku)
                    .build();
            httpGet.setURI(uri);
            HttpResponse httpResponseVariant = httpClient.execute(httpGet);
            String json = EntityUtils.toString(httpResponseVariant.getEntity());
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONObject data = (JSONObject) jsonObject.get("data");
            JSONObject variant = (JSONObject) data.get("Variant");
            int inventory = variant.getInt("inventory");
            return inventory;
    }

    private void postVariantInventory(String sku, int inventory, int quantity) throws URISyntaxException, IOException {
        inventory= inventory-quantity;
        HttpPost httpPost = new HttpPost(postVariantUrl);
        URI uriInventory = new URIBuilder(httpPost.getURI())
                .addParameter("sku", sku)
                .addParameter("inventory", String.valueOf(inventory))
                .build();
        httpPost.setURI(uriInventory);
        httpClient.execute(httpPost);
    }


    public List<DeliverrItem> getDeliverrItems(List<WishItem> wishItems){
        List<DeliverrItem> res = new ArrayList<>();

        for(WishItem item: wishItems) {
            DeliverrItem dItem = new DeliverrItem();
            dItem.setProductId(item.getProductId());
            dItem.setSku(item.getSku());
            dItem.setTitle(item.getDescription());
            res.add(dItem);
        }
        return res;
    }

    private class Inventory{
        String id;

    }

    public static CloseableHttpClient getHttpClient() {
        HttpRequestRetryHandler retryHandler = new HttpRequestRetryHandler() {
            public boolean retryRequest(
                    IOException exception,
                    int executionCount,
                    HttpContext context) {
                if (executionCount >= 5) {
                    // Do not retry if over max retry count
                    return false;
                }
                if (exception instanceof InterruptedIOException) {
                    // Timeout
                    return false;
                }
                if (exception instanceof UnknownHostException) {
                    // Unknown host
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {
                    // Connection refused
                    return false;
                }
                if (exception instanceof SSLException) {
                    // SSL handshake exception
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
                if (idempotent) {
                    // Retry if the request is considered idempotent
                    return true;
                }
                return false;
            }
        };
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(MAX_TOTAL);
        cm.setDefaultMaxPerRoute(MAX_PERROUTE);
        CloseableHttpClient httpClient = HttpClients
                .custom()
                .setRetryHandler(retryHandler)
                .setConnectionManager(cm)
                .build();
        return httpClient;
    }



}









