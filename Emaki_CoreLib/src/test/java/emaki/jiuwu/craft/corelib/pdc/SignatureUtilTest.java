package emaki.jiuwu.craft.corelib.pdc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignatureUtilTest {

    private record SampleRecord(Map<String, Object> values, String name) {
    }

    @Test
    void stableSignatureIgnoresMapOrderingInsideRecords() {
        Map<String, Object> leftValues = new LinkedHashMap<>();
        leftValues.put("a", 1);
        leftValues.put("b", 2);

        Map<String, Object> rightValues = new LinkedHashMap<>();
        rightValues.put("b", 2);
        rightValues.put("a", 1);

        SampleRecord left = new SampleRecord(leftValues, "demo");
        SampleRecord right = new SampleRecord(rightValues, "demo");

        assertEquals(SignatureUtil.stableSignature(left), SignatureUtil.stableSignature(right));
    }
}
