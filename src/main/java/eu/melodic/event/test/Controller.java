package eu.melodic.event.test;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Slf4j
@RestController
@AllArgsConstructor(onConstructor = @__({@Autowired}))
public class Controller {
    @Autowired
    private Coordinator coordinator;

    /*@RequestMapping(value = {"/", "/test"}, method = POST)
    public String test(@RequestBody String requestStr) {
        log.info("test(): {}", requestStr);
        return "OK";
    }*/

    @RequestMapping(value = "/startProcess", method = {GET, POST})
    public String startProcess() throws MalformedURLException, URISyntaxException {
        log.info("startProcess():");
        coordinator.startProcess();
        return "OK";
    }

    @RequestMapping(value = "/updateConfiguration", method = POST)
    public String metasolverUpdateConfiguration(@RequestBody String requestStr) {
        log.info("metasolverUpdateConfiguration(): {}", requestStr);
        return "OK";
    }

    @RequestMapping(value = "/camelModelProcessed/{notificationUri}", method = POST)
    public String esbCamelModelProcessed(@PathVariable("notificationUri") String notificationUri,
                                         @RequestBody String requestStr)
    {
        log.info("esbCamelModelProcessed(): notificationUri: {}", notificationUri);
        log.info("esbCamelModelProcessed(): Request Body: \n{}", requestStr);

        coordinator.scheduleMetricValuesUpdate();

        return "OK";
    }

    @RequestMapping(value = "/cpModelProcessed", method = POST)
    public String esbCpModelProcessed(@RequestBody String requestStr) {
        log.info("esbCpModelProcessed(): Request Body: {}", requestStr);

        return "OK";
    }

    @RequestMapping(value = "/**", method = POST)
    public String allEndpoints(HttpServletRequest req, @RequestBody String requestStr) {
        log.info("allEndpoints(): endpoint: {}", req.getRequestURI());
        log.info("allEndpoints(): request:  {}", requestStr);
        return "OK";
    }
}
