package court;

public record Decision( String id,
                        String type,
                        String content,
                        String reason,
                        String createdAt) {
}
