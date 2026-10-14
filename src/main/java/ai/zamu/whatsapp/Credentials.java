package ai.zamu.whatsapp;

/** What a Cloud API call needs. Lives only for the duration of a call; never logged. */
record Credentials(String phoneNumberId, String wabaId, String accessToken) {

    @Override
    public String toString() {
        return "Credentials[phoneNumberId=" + phoneNumberId + ", accessToken=***]";
    }
}
