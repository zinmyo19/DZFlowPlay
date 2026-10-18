import com.zynelabs.flowplay.data.AutoRefreshPolicy;

/** Standalone regression test: javac then java -ea (no Android/JUnit dependency). */
public final class AutoRefreshPolicyTest {
    private static final long MINUTE = 60_000L;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AutoRefreshPolicy p = new AutoRefreshPolicy();
        check(!p.shouldRefresh(null, 0, false, false), "null account");
        check(!p.shouldRefresh("", 0, false, false), "empty account");

        check(p.shouldRefresh("a", 0, false, false), "first launch refresh");
        check(p.begin("a", 0), "first claim");
        check(!p.begin("a", 1), "duplicate begin rejected");
        check(!p.shouldRefresh("a", 1, false, false), "in-flight duplicate rejected");
        p.finish("a", true, 1);
        check(!p.shouldRefresh("a", 1 + 14 * MINUTE, false, false), "fresh success skipped");
        check(p.shouldRefresh("a", 1 + 15 * MINUTE, false, false), "aged success refresh");

        check(p.begin("a", 1 + 15 * MINUTE), "aged claim");
        p.finish("a", false, 1 + 15 * MINUTE);
        check(!p.shouldRefresh("a", 1 + 15 * MINUTE + 59_999L, false, false), "failure cooldown");
        check(p.shouldRefresh("a", 1 + 16 * MINUTE, false, false), "failed retry");

        check(p.begin("a", 1 + 16 * MINUTE), "retry claim");
        p.finish("a", true, 1 + 16 * MINUTE);
        check(!p.shouldRefresh("a", 1 + 16 * MINUTE + 59_999L, false, true), "schema retry cooldown");
        check(p.shouldRefresh("a", 1 + 16 * MINUTE + MINUTE, false, true), "schema retry");

        check(p.begin("a", 17 * MINUTE), "manual bypass cooldown");
        p.finish("a", true, 17 * MINUTE);
        check(!p.shouldRefresh("a", 17 * MINUTE + 29_999L, true, false), "provider debounce");
        check(p.shouldRefresh("a", 17 * MINUTE + 30_000L, true, false), "provider switch after debounce");

        check(p.shouldRefresh("b", 0, false, false), "second account independent");
        check(p.begin("b", 0), "second account claim");
        check(!p.shouldRefresh("b", 1, false, false), "second account in flight");
        check(!p.shouldRefresh("a", 17 * MINUTE + 30_000L, false, false), "account a remains independent");
        p.finish("b", true, 0);
        check(!p.shouldRefresh("b", 1, false, false), "second account completion recorded");

        // A backwards wall clock is fail-closed, and must not create a retry storm.
        check(!p.shouldRefresh("a", 1, false, false), "clock regression safe");
        check(!p.shouldRefresh("a", 2, false, false), "clock regression remains safe");
        System.out.println("AutoRefreshPolicyTest OK");
    }
}
