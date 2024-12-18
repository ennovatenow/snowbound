package io.paycorp.smartmandate.demo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.paycorp.smartmandate.client.ApiClient;
import io.paycorp.smartmandate.client.Client;
import io.paycorp.smartmandate.client.domain.Mandate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/")
public class IndexController {

    private static final SecureRandom random = new SecureRandom();

    @Value("${paycorp.client.id}")
    private String clientId;

    @Value("${paycorp.encryption.key}")
    private String encryptionKey;

    @GetMapping("/")
    public String index() {
        return "sign-in";
    }

    @GetMapping("/home")
    public String index(Model model) {
        Map<String, String> accountTypeMap = Mandate.Nach.accountTypeMap();
        Map<String, String> frqcyMap = Mandate.Nach.frqcyMap();

        model.addAttribute("accountTypeMap", accountTypeMap);
        model.addAttribute("frqcyMap", frqcyMap);
        return "home";
    }

    @GetMapping("/queryMandate")
    public String queryMandate(Model model) {
        return "query_mandate";
    }

    @PostMapping("/queryMandate")
    public String postQueryMandate(@RequestParam String referenceNumber, @RequestParam String encryptionKey,
            @RequestParam String url, @RequestParam String apiKey, Model model) throws Exception {
        log.info("Reference Number: " + referenceNumber);
        log.info("Encryption Key: " + encryptionKey);
        log.info("URL: " + url);
        log.info("API Key: " + apiKey);

        model.addAttribute("referenceNumber", referenceNumber);
        model.addAttribute("encryptionKey", encryptionKey);
        model.addAttribute("url", url);
        model.addAttribute("apiKey", apiKey);

        var optEncData = HelperUtility.encrypt(encryptionKey, referenceNumber);
        if (optEncData.isPresent()) {
            var urlEnc = Base64.getUrlEncoder().encodeToString(optEncData.get().getBytes(StandardCharsets.UTF_8));
            model.addAttribute("encData", optEncData.get());
            model.addAttribute("urlEnc", urlEnc);
            var decoded = Base64.getUrlDecoder().decode(urlEnc);
            log.info("Decoded: " + new String(decoded, StandardCharsets.UTF_8));
            var decrypted = HelperUtility.decrypt(encryptionKey, new String(decoded, StandardCharsets.UTF_8));
            log.info("Decrypted: " + decrypted.orElse("Failed to decrypt"));
        } else {
            model.addAttribute("encData", "Failed to encrypt data");
            model.addAttribute("urlEnc", "Failed to encode data");
        }

        ApiClient apiClient = new ApiClient(url, apiKey, encryptionKey);
        var apiResponse = apiClient.findBySourceReference(referenceNumber);
        if(!apiResponse.isSuccess()) {
            model.addAttribute("encResponse", apiResponse.toString());
            model.addAttribute("decResponse", apiResponse.toString());
        } else {
            model.addAttribute("encResponse", apiResponse.message());
            model.addAttribute("decResponse", apiResponse.message());
        } 

        return "query_mandate";
    }

    @GetMapping("/createKey")
    public String createRandomAesKey(Model model) {
        byte[] key = new byte[32];
        random.nextBytes(key);
        model.addAttribute("key", Base64.getEncoder().encodeToString(key));
        return "create_key";
    }

    @PostMapping("/home")
    public String postIndex(Model model,
            @RequestParam String consumerRefNumber,
            @RequestParam String referenceNumber,
            @RequestParam double amount,
            @RequestParam String frqcy,
            @RequestParam String firstCollectionDate,
            @RequestParam String finalCollectionDate,
            @RequestParam String dbtrNm,
            @RequestParam String mobile,
            @RequestParam String dbtrAccNo,
            @RequestParam String dbtrAccTp,
            @RequestParam String bnkId) {

        log.info("Consumer Reference Number: " + consumerRefNumber);
        log.info("Reference Number: " + referenceNumber);
        log.info("Amount: " + amount);
        log.info("Frequency: " + frqcy);
        log.info("First Collection Date: " + firstCollectionDate);
        log.info("Final Collection Date: " + finalCollectionDate);
        log.info("Debtor Name: " + dbtrNm);
        log.info("Mobile: " + mobile);
        log.info("Debtor Account Number: " + dbtrAccNo);
        log.info("Debtor Account Type: " + dbtrAccTp);
        log.info("Bank ID: " + bnkId);

        Mandate mandate = new Mandate.Builder()
                .utilityCode("NACH0000MUTHPLBARB")
                .schmNm("Vehicle")
                .consRefNo(consumerRefNumber)
                .sourceReferenceNumber(referenceNumber)
                .colltnAmt(new BigDecimal(amount))
                .frqcy(Mandate.Nach.Frqcy.valueOf(frqcy))
                .frstColltnDt(LocalDate.parse(firstCollectionDate))
                .fnlColltnDt(LocalDate.parse(finalCollectionDate))
                .dbtrNm(dbtrNm)
                .mobile(mobile)
                .dbtrAccNo(dbtrAccNo)
                .dbtrAccTp(Mandate.Nach.AccountType.valueOf(dbtrAccTp))
                .bnkId(bnkId)
                .build();

        var optionalEncData = Client.encrypt(encryptionKey, mandate);
        var encData = optionalEncData.orElseThrow(() -> new RuntimeException("Failed to encrypt data"));
        log.info("Encrypted Data: " + encData);

        model.addAttribute("encData", encData);
        model.addAttribute("clientId", clientId);
        return "redirect_to_gateway";
    }
}
// { "accNum": "12344555" } -> encrypt and send