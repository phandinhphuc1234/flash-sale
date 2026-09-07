package com.philia.flashsale.inventory.configuration;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Inventory-owned runtime policy for the disabled-by-default regular hold capability. */
@ConfigurationProperties(prefix = "flashsale.inventory.regular-hold")
public class InventoryRegularHoldProperties {
    private boolean apiEnabled;
    private boolean commandConsumerEnabled;
    private boolean outboxPublisherEnabled;
    private boolean expiryEnabled;
    private int expiryBatchSize = 100;
    private Duration ttl = Duration.ofMinutes(5);
    private Duration maxClockSkew = Duration.ofSeconds(90);
    private String commandsTopic;
    private String eventsTopic;
    private String commandConsumerGroup;
    private String commandDltTopic;
    private List<Duration> retryDelays = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10));

    public void validate() {
        if (!Duration.ofMinutes(5).equals(ttl)) {
            throw new IllegalArgumentException("Regular hold TTL must remain exactly five minutes");
        }
        if (maxClockSkew == null || maxClockSkew.isNegative() || maxClockSkew.isZero()) {
            throw new IllegalArgumentException("Regular hold maxClockSkew must be positive");
        }
        if (retryDelays == null || retryDelays.isEmpty()
                || retryDelays.stream().anyMatch(delay -> delay == null || delay.isNegative() || delay.isZero())) {
            throw new IllegalArgumentException("Regular hold retry delays must be positive");
        }
        if (expiryBatchSize <= 0 || expiryBatchSize > 1_000) {
            throw new IllegalArgumentException("Regular hold expiryBatchSize must be between one and one thousand");
        }
    }

    public boolean isApiEnabled() { return apiEnabled; }
    public void setApiEnabled(boolean apiEnabled) { this.apiEnabled = apiEnabled; }
    public boolean isCommandConsumerEnabled() { return commandConsumerEnabled; }
    public void setCommandConsumerEnabled(boolean commandConsumerEnabled) { this.commandConsumerEnabled = commandConsumerEnabled; }
    public boolean isOutboxPublisherEnabled() { return outboxPublisherEnabled; }
    public void setOutboxPublisherEnabled(boolean outboxPublisherEnabled) { this.outboxPublisherEnabled = outboxPublisherEnabled; }
    public boolean isExpiryEnabled() { return expiryEnabled; }
    public void setExpiryEnabled(boolean expiryEnabled) { this.expiryEnabled = expiryEnabled; }
    public int getExpiryBatchSize() { return expiryBatchSize; }
    public void setExpiryBatchSize(int expiryBatchSize) { this.expiryBatchSize = expiryBatchSize; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getMaxClockSkew() { return maxClockSkew; }
    public void setMaxClockSkew(Duration maxClockSkew) { this.maxClockSkew = maxClockSkew; }
    public String getCommandsTopic() { return commandsTopic; }
    public void setCommandsTopic(String commandsTopic) { this.commandsTopic = commandsTopic; }
    public String getEventsTopic() { return eventsTopic; }
    public void setEventsTopic(String eventsTopic) { this.eventsTopic = eventsTopic; }
    public String getCommandConsumerGroup() { return commandConsumerGroup; }
    public void setCommandConsumerGroup(String commandConsumerGroup) { this.commandConsumerGroup = commandConsumerGroup; }
    public String getCommandDltTopic() { return commandDltTopic; }
    public void setCommandDltTopic(String commandDltTopic) { this.commandDltTopic = commandDltTopic; }
    public List<Duration> getRetryDelays() { return List.copyOf(retryDelays); }
    public void setRetryDelays(List<Duration> retryDelays) { this.retryDelays = List.copyOf(retryDelays); }
}
