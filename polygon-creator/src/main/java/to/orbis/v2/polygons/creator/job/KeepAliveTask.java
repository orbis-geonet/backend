package to.orbis.v2.polygons.creator.job;

import com.google.appengine.repackaged.org.apache.http.client.methods.CloseableHttpResponse;
import com.google.appengine.repackaged.org.apache.http.client.methods.HttpGet;
import com.google.appengine.repackaged.org.apache.http.client.utils.URIBuilder;
import com.google.appengine.repackaged.org.apache.http.impl.client.CloseableHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeepAliveTask {
    private final static String ADMIN_URL = "https://admin-dot-orbisv2-production.uc.r.appspot.com/api/v1/keep-alive";

    private final CloseableHttpClient client;

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void checkKeepAlive() throws URISyntaxException, IOException {
        URIBuilder uriBuilder = new URIBuilder(ADMIN_URL);
        HttpGet httpGet = new HttpGet(uriBuilder.build());

        CloseableHttpResponse response = client.execute(httpGet);

        if (response.getStatusLine().getStatusCode() == 200) {
            log.info("checkKeepAlive: app is alive");
        } else {
            log.info("checkKeepAlive: app is NOT alive");
        }
    }

}
