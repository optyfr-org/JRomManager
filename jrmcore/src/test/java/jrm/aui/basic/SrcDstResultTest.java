package jrm.aui.basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

/**
 * Tests for {@link SrcDstResult}, {@link AbstractSrcDstResult} JSON serialization,
 * and the {@link SDRList} container.
 */
@DisplayName("SrcDstResult tests")
class SrcDstResultTest {

    @Nested
    @DisplayName("constructors and defaults")
    class ConstructorsAndDefaults {

        @Test
        @DisplayName("default constructor should leave all fields null/false")
        void defaultConstructorShouldLeaveAllFieldsNullOrFalse() {
            final var sdr = new SrcDstResult();

            assertThat(sdr.getId()).isNull();
            assertThat(sdr.getSrc()).isNull();
            assertThat(sdr.getDst()).isNull();
            assertThat(sdr.getResult()).isNull();
            assertThat(sdr.isSelected()).isFalse();
        }

        @Test
        @DisplayName("src constructor should generate a non-null id")
        void srcConstructorShouldGenerateNonNullId() {
            final var sdr = new SrcDstResult("source");

            assertThat(sdr.getSrc()).isEqualTo("source");
            assertThat(sdr.getId()).isNotNull();
            assertThat(sdr.getDst()).isNull();
            assertThat(sdr.getResult()).isNull();
            assertThat(sdr.isSelected()).isFalse();
        }

        @Test
        @DisplayName("src/dst constructor should generate a non-null id")
        void srcDstConstructorShouldGenerateNonNullId() {
            final var sdr = new SrcDstResult("source", "destination");

            assertThat(sdr.getSrc()).isEqualTo("source");
            assertThat(sdr.getDst()).isEqualTo("destination");
            assertThat(sdr.getId()).isNotNull();
        }

        @Test
        @DisplayName("two src constructors should generate distinct ids")
        void twoSrcConstructorsShouldGenerateDistinctIds() {
            final var a = new SrcDstResult("s");
            final var b = new SrcDstResult("s");

            assertThat(a.getId()).isNotEqualTo(b.getId());
        }
    }

    @Nested
    @DisplayName("getters and setters")
    class GettersAndSetters {

        @Test
        @DisplayName("should set and get all mutable fields")
        void shouldSetAndGetAllMutableFields() {
            final var sdr = new SrcDstResult();

            sdr.setSrc("src-value");
            sdr.setDst("dst-value");
            sdr.setResult("result-value");
            sdr.setSelected(true);

            assertThat(sdr.getSrc()).isEqualTo("src-value");
            assertThat(sdr.getDst()).isEqualTo("dst-value");
            assertThat(sdr.getResult()).isEqualTo("result-value");
            assertThat(sdr.isSelected()).isTrue();
        }

        @Test
        @DisplayName("should toggle selection state")
        void shouldToggleSelectionState() {
            final var sdr = new SrcDstResult();

            sdr.setSelected(true);
            assertThat(sdr.isSelected()).isTrue();

            sdr.setSelected(false);
            assertThat(sdr.isSelected()).isFalse();
        }
    }

    @Nested
    @DisplayName("JSON serialization round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("toJSONObject should serialize all fields")
        void toJsonObjectShouldSerializeAllFields() {
            final var sdr = new SrcDstResult("source", "dest");
            sdr.setResult("ok");
            sdr.setSelected(true);

            final var json = sdr.toJSONObject();

            assertThat(json.get("src").asString()).isEqualTo("source");
            assertThat(json.get("dst").asString()).isEqualTo("dest");
            assertThat(json.get("result").asString()).isEqualTo("ok");
            assertThat(json.getBoolean("selected", false)).isTrue();
            assertThat(json.get("id").asString()).isEqualTo(sdr.getId());
        }

        @Test
        @DisplayName("round-trip via toJSONObject/fromJSONObject should preserve fields")
        void roundTripShouldPreserveFields() {
            final var original = new SrcDstResult("source-a", "dest-a");
            original.setResult("success");
            original.setSelected(false);

            final var json = original.toJSONObject();
            final var restored = new SrcDstResult(json);

            assertThat(restored.getSrc()).isEqualTo("source-a");
            assertThat(restored.getDst()).isEqualTo("dest-a");
            assertThat(restored.getResult()).isEqualTo("success");
            assertThat(restored.isSelected()).isFalse();
            assertThat(restored.getId()).isEqualTo(original.getId());
        }

        @Test
        @DisplayName("fromJSONObject should skip assignment when optional fields are absent")
        void fromJsonObjectShouldSkipAssignmentWhenOptionalFieldsAreAbsent() {
            final var sdr = new SrcDstResult();
            sdr.fromJSONObject(new JsonObject());

            assertThat(sdr.getId()).isNull();
            assertThat(sdr.getSrc()).isNull();
            assertThat(sdr.getDst()).isNull();
            assertThat(sdr.getResult()).isNull();
            assertThat(sdr.isSelected()).isTrue();
        }

        @Test
        @DisplayName("fromJSONObject should skip assignment when optional fields are Json.NULL")
        void fromJsonObjectShouldSkipAssignmentWhenOptionalFieldsAreJsonNull() {
            final var json = new JsonObject();
            json.add("id", Json.NULL);
            json.add("src", Json.NULL);
            json.add("dst", Json.NULL);
            json.add("result", Json.NULL);

            final var sdr = new SrcDstResult();
            sdr.setSrc("keep-src");
            sdr.setDst("keep-dst");
            sdr.setResult("keep-result");
            sdr.fromJSONObject(json);

            assertThat(sdr.getId()).isNull();
            assertThat(sdr.getSrc()).isEqualTo("keep-src");
            assertThat(sdr.getDst()).isEqualTo("keep-dst");
            assertThat(sdr.getResult()).isEqualTo("keep-result");
        }

        @Test
        @DisplayName("fromJSONObject should default selected to true when key absent")
        void fromJsonObjectShouldDefaultSelectedToTrueWhenKeyAbsent() {
            final var json = new JsonObject();
            json.add("src", "only-src");
            json.add("dst", "only-dst");
            json.add("result", "r");

            final var sdr = new SrcDstResult();
            sdr.fromJSONObject(json);

            assertThat(sdr.isSelected()).isTrue();
        }

        @Test
        @DisplayName("fromJSONObject should set explicit selected value")
        void fromJsonObjectShouldSetExplicitSelectedValue() {
            final var json = new JsonObject();
            json.add("src", "s");
            json.add("dst", "d");
            json.add("result", "r");
            json.add("selected", false);

            final var sdr = new SrcDstResult();
            sdr.fromJSONObject(json);

            assertThat(sdr.isSelected()).isFalse();
        }
    }

    @Nested
    @DisplayName("list JSON conversion")
    class ListJsonConversion {

        @Test
        @DisplayName("toJSON should serialize a list as a JSON array")
        void toJsonShouldSerializeListAsJsonArray() {
            final var a = new SrcDstResult("src-a", "dst-a");
            a.setResult("ra");
            final var b = new SrcDstResult("src-b", "dst-b");
            b.setResult("rb");

            final var json = AbstractSrcDstResult.toJSON(List.of(a, b));

            final var arr = Json.parse(json).asArray();
            assertThat(arr).hasSize(2);
            assertThat(arr.get(0).asObject().get("src").asString()).isEqualTo("src-a");
            assertThat(arr.get(1).asObject().get("src").asString()).isEqualTo("src-b");
        }

        @Test
        @DisplayName("toJSON should serialize an empty list as empty array")
        void toJsonShouldSerializeEmptyListAsEmptyArray() {
            final var json = AbstractSrcDstResult.toJSON(List.of());

            assertThat(Json.parse(json).asArray()).isEmpty();
        }

        @Test
        @DisplayName("fromJSON should deserialize a JSON array back into an SDRList")
        void fromJsonShouldDeserializeJsonArrayBackIntoSdrList() {
            final var a = new SrcDstResult("src-a", "dst-a");
            a.setResult("ra");
            final var b = new SrcDstResult("src-b", "dst-b");
            b.setResult("rb");
            final var json = AbstractSrcDstResult.toJSON(List.of(a, b));

            final SDRList<SrcDstResult> restored = SrcDstResult.fromJSON(json);

            assertThat(restored).hasSize(2);
            assertThat(restored.get(0).getSrc()).isEqualTo("src-a");
            assertThat(restored.get(1).getSrc()).isEqualTo("src-b");
            assertThat(restored.get(0).getId()).isEqualTo(a.getId());
        }

        @Test
        @DisplayName("fromJSON should flag needSave and assign id when id is missing")
        void fromJsonShouldFlagNeedSaveAndAssignIdWhenIdMissing() {
            final var json = "[{\"src\":\"s\",\"dst\":\"d\",\"result\":\"r\",\"selected\":true}]";

            final SDRList<SrcDstResult> restored = SrcDstResult.fromJSON(json);

            assertThat(restored).hasSize(1);
            assertThat(restored.isNeedSave()).isTrue();
            assertThat(restored.get(0).getId()).isNotNull();
        }

        @Test
        @DisplayName("fromJSON should not flag needSave when all ids are present")
        void fromJsonShouldNotFlagNeedSaveWhenAllIdsPresent() {
            final var json = "[{\"id\":\"fixed-id\",\"src\":\"s\",\"dst\":\"d\",\"result\":\"r\",\"selected\":true}]";

            final SDRList<SrcDstResult> restored = SrcDstResult.fromJSON(json);

            assertThat(restored.isNeedSave()).isFalse();
            assertThat(restored.get(0).getId()).isEqualTo("fixed-id");
        }

        @Test
        @DisplayName("fromJSON should return empty list for empty JSON array")
        void fromJsonShouldReturnEmptyListForEmptyJsonArray() {
            final SDRList<SrcDstResult> restored = SrcDstResult.fromJSON("[]");

            assertThat(restored).isEmpty();
            assertThat(restored.isNeedSave()).isFalse();
        }
    }

    @Nested
    @DisplayName("SDRList")
    class SdrListTests {

        @Test
        @DisplayName("needSave should default to false")
        void needSaveShouldDefaultToFalse() {
            final var list = new SDRList<SrcDstResult>();

            assertThat(list.isNeedSave()).isFalse();
        }

        @Test
        @DisplayName("should add and retrieve elements like an ArrayList")
        void shouldAddAndRetrieveElementsLikeArrayList() {
            final var list = new SDRList<SrcDstResult>();
            final var sdr = new SrcDstResult("s");

            list.add(sdr);

            assertThat(list).hasSize(1);
            assertThat(list.get(0)).isSameAs(sdr);
        }

        @Test
        @DisplayName("equals should return true for SDRList with same contents")
        void equalsShouldReturnTrueForSdrListWithSameContents() {
            final var shared = new SrcDstResult("s");
            final var listA = new SDRList<SrcDstResult>();
            listA.add(shared);
            final var listB = new SDRList<SrcDstResult>();
            listB.add(shared);

            assertThat(listA).isEqualTo(listB).hasSameHashCodeAs(listB);
        }

        @Test
        @DisplayName("equals should return false for non-SDRList")
        void equalsShouldReturnFalseForNonSdrList() {
            final var list = new SDRList<SrcDstResult>();
            list.add(new SrcDstResult("s"));

            assertThat(list.equals(List.of(new SrcDstResult("s")))).isFalse();
        }

        @Test
        @DisplayName("equals should return false for SDRList with different contents")
        void equalsShouldReturnFalseForSdrListWithDifferentContents() {
            final var listA = new SDRList<SrcDstResult>();
            listA.add(new SrcDstResult("s1"));
            final var listB = new SDRList<SrcDstResult>();
            listB.add(new SrcDstResult("s2"));

            assertThat(listA).isNotEqualTo(listB);
        }
    }
}
