package com.supermarket.pos.products;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.products.entity.BarcodeType;
import com.supermarket.pos.products.service.BarcodeNormalizer;
import com.supermarket.pos.products.service.BarcodeNormalizer.Normalized;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarcodeNormalizerTest {

    @Test
    void validEan13IsKept() {
        assertThat(BarcodeNormalizer.normalize("4006381333931"))
                .isEqualTo(new Normalized("4006381333931", BarcodeType.EAN_13));
    }

    @Test
    void upcAIsStoredAsGtin13SoBothScansMatch() {
        Normalized upcA = BarcodeNormalizer.normalize("036000291452");
        Normalized ean13 = BarcodeNormalizer.normalize("0036000291452");
        assertThat(upcA.value()).isEqualTo("0036000291452");
        assertThat(upcA.type()).isEqualTo(BarcodeType.UPC_A);
        assertThat(ean13.value()).isEqualTo(upcA.value());
    }

    @Test
    void upcEIsExpandedToTheSameGtinAsItsUpcA() {
        // UPC-E 04252614 expands to UPC-A 042100005264
        assertThat(BarcodeNormalizer.expandUpcE("04252614")).isEqualTo("042100005264");
        Normalized upcE = BarcodeNormalizer.normalize("04252614", "UPC_E");
        assertThat(upcE.value()).isEqualTo("0042100005264");
        assertThat(BarcodeNormalizer.normalize("042100005264").value()).isEqualTo(upcE.value());
    }

    @Test
    void ean8WithValidCheckDigit() {
        assertThat(BarcodeNormalizer.normalize("96385074"))
                .isEqualTo(new Normalized("96385074", BarcodeType.EAN_8));
    }

    @Test
    void gtin14WithLeadingZeroBecomesGtin13() {
        assertThat(BarcodeNormalizer.normalize("04006381333931").value()).isEqualTo("4006381333931");
    }

    @Test
    void spacesAndDashesAreIgnored() {
        assertThat(BarcodeNormalizer.normalize(" 400-6381 333931 ").value()).isEqualTo("4006381333931");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4006381333932", "036000291453", "96385075"})
    void wrongCheckDigitsAreRejected(String code) {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize(code))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INVALID_BARCODE);
    }

    @Test
    void alphanumericCodesAreCode128AndCaseSensitive() {
        assertThat(BarcodeNormalizer.normalize("ABC-123x").value()).isEqualTo("ABC123x");
        assertThat(BarcodeNormalizer.normalize("ABC-123x").type()).isEqualTo(BarcodeType.CODE_128);
    }

    @Test
    void shortNumericCodesAreInternal() {
        assertThat(BarcodeNormalizer.normalize("2001").type()).isEqualTo(BarcodeType.INTERNAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abcé", "line\nbreak"})
    void emptyOrUnprintableInputIsRejected(String code) {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize(code)).isInstanceOf(ApiException.class);
    }

    @Test
    void tooLongCodesAreRejected() {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize("1".repeat(40))).isInstanceOf(ApiException.class);
    }

    @Test
    void checkDigitAlgorithm() {
        assertThat(BarcodeNormalizer.checkDigit("400638133393")).isEqualTo(1);
        assertThat(BarcodeNormalizer.checkDigit("03600029145")).isEqualTo(2);
    }
}
