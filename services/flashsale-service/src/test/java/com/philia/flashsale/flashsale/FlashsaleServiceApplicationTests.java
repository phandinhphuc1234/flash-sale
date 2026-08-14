package com.philia.flashsale.flashsale;

import com.philia.flashsale.flashsale.reservation.application.port.in.GetOwnedReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.in.SubmitReservationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class FlashsaleServiceApplicationTests {

    @MockBean
    private SubmitReservationUseCase submitReservationUseCase;

    @MockBean
    private GetOwnedReservationUseCase getOwnedReservationUseCase;

    @Test
    void contextLoads() {
    }
}
