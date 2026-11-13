package com.stitchstyle.app.dto;

import com.stitchstyle.app.dto.request.BannerRequest;
import com.stitchstyle.app.dto.request.CartLineRequest;
import com.stitchstyle.app.dto.request.CheckoutCustomer;
import com.stitchstyle.app.dto.request.ComplaintRequest;
import com.stitchstyle.app.dto.request.CouponRequest;
import com.stitchstyle.app.dto.request.CreateSupportConversationRequest;
import com.stitchstyle.app.dto.request.OrderRequest;
import com.stitchstyle.app.dto.request.ProductRequest;
import com.stitchstyle.app.dto.request.ReturnRequestCreate;
import com.stitchstyle.app.dto.request.ReviewRequest;
import com.stitchstyle.app.dto.request.SettingRequest;
import com.stitchstyle.app.dto.request.StaffRequest;
import com.stitchstyle.app.dto.request.TrackingRequest;
import com.stitchstyle.app.dto.request.VariantRequest;
import com.stitchstyle.app.model.entity.PaymentMethod;
import com.stitchstyle.app.model.entity.ReturnCondition;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.SizeType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void checkoutCustomerRequiresAValidBangladeshiPhone() {
        assertThat(validator.validate(new CheckoutCustomer("Nusrat", "01712345678"))).isEmpty();
        assertThat(validator.validate(new CheckoutCustomer("Nusrat", "+8801712345678"))).isEmpty();
        assertThat(validator.validate(new CheckoutCustomer(" ", "01712345678"))).isNotEmpty();
        assertThat(validator.validate(new CheckoutCustomer("Nusrat", "12345"))).isNotEmpty();
    }

    @Test
    void cartLinesRequireAVariantAndPositiveQuantity() {
        assertThat(validator.validate(new CartLineRequest(1L, 1, true))).isEmpty();
        assertThat(validator.validate(new CartLineRequest(null, 1, true))).isNotEmpty();
        assertThat(validator.validate(new CartLineRequest(1L, 0, true))).isNotEmpty();
    }

    @Test
    void orderRequestRequiresCustomerAddressAreaMethodAndItems() {
        OrderRequest valid = new OrderRequest(new CheckoutCustomer("N", "01712345678"), "Dhaka",
                1L, PaymentMethod.COD, null, null, List.of(new CartLineRequest(1L, 1, true)));
        assertThat(validator.validate(valid)).isEmpty();

        OrderRequest invalid = new OrderRequest(null, " ", null, null, null, null, List.of());
        assertThat(validator.validate(invalid)).hasSizeGreaterThanOrEqualTo(4);

        OrderRequest badLine = new OrderRequest(new CheckoutCustomer("N", "01712345678"), "Dhaka",
                1L, PaymentMethod.COD, null, null, List.of(new CartLineRequest(1L, 0, true)));
        assertThat(validator.validate(badLine)).isNotEmpty();
    }

    @Test
    void productAndVariantRequestsEnforcePricesSkusAndNames() {
        ProductRequest valid = new ProductRequest(1L, Map.of("en", "Panjabi"), null, null,
                new BigDecimal("1000.00"), null, SizeType.LETTER, null, null, null, null, true, false,
                List.of(new VariantRequest(null, "M", "SKU-1", 5, 1, null, true)), List.of());
        assertThat(validator.validate(valid)).isEmpty();

        ProductRequest invalid = new ProductRequest(null, Map.of(), null, null, null,
                new BigDecimal("-1.00"), null, null, null, null, null, null, null,
                List.of(new VariantRequest(null, " ", " ", -1, null, null, null)), null);
        assertThat(validator.validate(invalid)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void returnRequestRequiresItemsAndValidQuantities() {
        ReturnRequestCreate valid = new ReturnRequestCreate("ORD-1", "reason",
                List.of(new ReturnRequestCreate.Item(1L, 1, ReturnCondition.UNOPENED)));
        assertThat(validator.validate(valid)).isEmpty();

        ReturnRequestCreate invalid = new ReturnRequestCreate(" ", null,
                List.of(new ReturnRequestCreate.Item(null, 0, null)));
        assertThat(validator.validate(invalid)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void staffEmailAndRoleAreValidated() {
        assertThat(validator.validate(new StaffRequest("admin@x.com", "Admin", Role.ADMIN, true))).isEmpty();
        assertThat(validator.validate(new StaffRequest("not-an-email", "Admin", null, true)))
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void couponRequiresCodeTypeAndPositiveValue() {
        assertThat(validator.validate(new CouponRequest("SAVE", "PERCENT", BigDecimal.ONE, null,
                null, null, null, true))).isEmpty();
        assertThat(validator.validate(new CouponRequest(" ", " ", BigDecimal.ZERO, null,
                null, null, null, true))).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void bannerRequiresTitleAndImage() {
        assertThat(validator.validate(new BannerRequest("Sale", 1L, null, 0, true, null, null))).isEmpty();
        assertThat(validator.validate(new BannerRequest(" ", null, null, 0, true, null, null)))
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void complaintRequiresNamePhoneMessage() {
        assertThat(validator.validate(new ComplaintRequest("N", "01712345678", null, "Help"))).isEmpty();
        assertThat(validator.validate(new ComplaintRequest(" ", "123", null, " "))).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void reviewRatingMustBeBetweenOneAndFive() {
        assertThat(validator.validate(new ReviewRequest(5, "Great"))).isEmpty();
        assertThat(validator.validate(new ReviewRequest(0, null))).isNotEmpty();
        assertThat(validator.validate(new ReviewRequest(6, null))).isNotEmpty();
    }

    @Test
    void chatMessagesRequireAClientIdAndBoundedBody() {
        assertThat(validator.validate(new CreateSupportConversationRequest(null, "client", "Hello"))).isEmpty();
        assertThat(validator.validate(new CreateSupportConversationRequest(null, " ", " ")))
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(validator.validate(new CreateSupportConversationRequest(null, "client", "x".repeat(4001))))
                .isNotEmpty();
    }

    @Test
    void settingAndTrackingRequireTheirKeys() {
        assertThat(validator.validate(new SettingRequest(" ", null, null))).isNotEmpty();
        assertThat(validator.validate(new TrackingRequest(" ", " "))).hasSize(2);
    }
}
