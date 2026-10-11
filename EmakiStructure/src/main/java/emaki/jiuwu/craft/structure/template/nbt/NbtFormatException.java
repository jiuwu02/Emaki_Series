package emaki.jiuwu.craft.structure.template.nbt;

public final class NbtFormatException extends RuntimeException {

    public enum Issue {

        GZIP_HEADER("gzip_header"),
        OUTPUT_LIMIT("output_limit"),
        DEPTH_EXCEEDED("depth_exceeded"),
        BAD_ROOT_TYPE("bad_root_type"),
        PARSE_BROKEN("parse_broken"),
        IO_FAILURE("io_failure");

        private final String code;

        Issue(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Issue issue;

    public NbtFormatException(Issue issue, String message) {
        super(message);
        this.issue = issue;
    }

    public NbtFormatException(Issue issue, String message, Throwable cause) {
        super(message, cause);
        this.issue = issue;
    }

    public Issue issue() {
        return issue;
    }
}
