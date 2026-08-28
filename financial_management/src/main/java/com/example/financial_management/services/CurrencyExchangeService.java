package com.example.financial_management.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.financial_management.constant.Currency;
import com.example.financial_management.entity.CurrencyExchange;
import com.example.financial_management.model.currency.CurrencyExchangeResponse;
import com.example.financial_management.repository.CurrencyExchangeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyExchangeService {

    private final CurrencyExchangeRepository currencyExchangeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final BigDecimal DEFAULT_USD_VND_RATE = BigDecimal.valueOf(25450.00);
    private static final String API_URL = "https://open.er-api.com/v6/latest/USD";

    private volatile BigDecimal cachedUsdToVndRate = DEFAULT_USD_VND_RATE;

    @PostConstruct
    public void init() {
        loadLatestRateFromDb();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Tự động kiểm tra và sync tỷ giá hôm nay khi ứng dụng khởi động thành công
        LocalDate today = LocalDate.now();
        if (!currencyExchangeRepository.existsByFromCurrencyAndToCurrencyAndExchangeDate(
                Currency.USD, Currency.VND, today)) {
            syncDailyRate();
        }
    }

    /**
     * Đồng bộ tỷ giá ngoại tệ USD/VND trực tuyến từ API
     */
    public CurrencyExchangeResponse syncDailyRate() {
        LocalDate today = LocalDate.now();
        BigDecimal fetchedRate = null;
        String source = "OpenExchangeRates API";

        try {
            log.info("Fetching daily USD to VND rate from {}", API_URL);
            ResponseEntity<String> response = restTemplate.getForEntity(API_URL, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode vndNode = root.path("rates").path("VND");

                if (!vndNode.isMissingNode() && vndNode.asDouble() > 0) {
                    fetchedRate = BigDecimal.valueOf(vndNode.asDouble()).setScale(2, RoundingMode.HALF_UP);
                    log.info("Successfully fetched USD to VND rate: {}", fetchedRate);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch exchange rate from API: {}. Using fallback.", e.getMessage());
        }

        if (fetchedRate == null) {
            fetchedRate = loadLatestRateFromDb();
            source = "Database / Fallback Default";
        }

        cachedUsdToVndRate = fetchedRate;

        // Lưu hoặc cập nhật vào database cho ngày hôm nay
        Optional<CurrencyExchange> existing = currencyExchangeRepository
                .findByFromCurrencyAndToCurrencyAndExchangeDate(Currency.USD, Currency.VND, today);

        CurrencyExchange exchange;
        if (existing.isPresent()) {
            exchange = existing.get();
            exchange.setRate(fetchedRate);
            exchange.setSource(source);
        } else {
            exchange = CurrencyExchange.builder()
                    .fromCurrency(Currency.USD)
                    .toCurrency(Currency.VND)
                    .fromCurrencyCode("USD")
                    .toCurrencyCode("VND")
                    .rate(fetchedRate)
                    .exchangeDate(today)
                    .source(source)
                    .build();
        }

        CurrencyExchange saved = currencyExchangeRepository.save(exchange);
        return toResponse(saved);
    }

    /**
     * Lấy tỷ giá USD -> VND hiện tại
     */
    public BigDecimal getCurrentRate() {
        if (cachedUsdToVndRate == null || cachedUsdToVndRate.compareTo(BigDecimal.ZERO) <= 0) {
            cachedUsdToVndRate = loadLatestRateFromDb();
        }
        return cachedUsdToVndRate;
    }

    /**
     * Quy đổi số tiền VND sang USD (làm tròn 2 chữ số thập phân)
     */
    public BigDecimal toUsd(BigDecimal amountVnd) {
        if (amountVnd == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = getCurrentRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amountVnd.divide(rate, 2, RoundingMode.HALF_UP);
    }

    /**
     * Quy đổi số tiền USD sang VND (làm tròn số nguyên)
     */
    public BigDecimal toVnd(BigDecimal amountUsd) {
        if (amountUsd == null) {
            return BigDecimal.ZERO;
        }
        return amountUsd.multiply(getCurrentRate()).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Tự động tính toán giá trị USD dựa trên loại tiền tệ gốc
     * Nếu tiền gốc là VND -> quy đổi sang USD
     * Nếu tiền gốc là USD -> giữ nguyên
     */
    public BigDecimal calculateUsd(BigDecimal amount, int currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (currency == Currency.USD) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        return toUsd(amount);
    }

    /**
     * Lấy thông tin tỷ giá mới nhất dưới dạng Response DTO
     */
    public CurrencyExchangeResponse getLatestRateResponse() {
        Optional<CurrencyExchange> latest = currencyExchangeRepository
                .findTopByFromCurrencyAndToCurrencyOrderByExchangeDateDesc(Currency.USD, Currency.VND);

        if (latest.isPresent()) {
            cachedUsdToVndRate = latest.get().getRate();
            return toResponse(latest.get());
        }

        // Nếu DB chưa có bản ghi nào, kích hoạt sync ngay lập tức
        return syncDailyRate();
    }

    private BigDecimal loadLatestRateFromDb() {
        Optional<CurrencyExchange> latest = currencyExchangeRepository
                .findTopByFromCurrencyAndToCurrencyOrderByExchangeDateDesc(Currency.USD, Currency.VND);

        if (latest.isPresent()) {
            cachedUsdToVndRate = latest.get().getRate();
            return cachedUsdToVndRate;
        }

        cachedUsdToVndRate = DEFAULT_USD_VND_RATE;
        return DEFAULT_USD_VND_RATE;
    }

    public List<CurrencyExchangeResponse> getRateHistory() {
        return currencyExchangeRepository
                .findAllByFromCurrencyAndToCurrencyOrderByExchangeDateDesc(Currency.USD, Currency.VND)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CurrencyExchangeResponse toResponse(CurrencyExchange entity) {
        return CurrencyExchangeResponse.builder()
                .id(entity.getId())
                .fromCurrency(entity.getFromCurrency())
                .toCurrency(entity.getToCurrency())
                .fromCurrencyCode(entity.getFromCurrencyCode())
                .toCurrencyCode(entity.getToCurrencyCode())
                .rate(entity.getRate())
                .exchangeDate(entity.getExchangeDate())
                .source(entity.getSource())
                .build();
    }
}
