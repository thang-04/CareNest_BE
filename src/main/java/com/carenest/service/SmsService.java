package com.carenest.service;

import com.carenest.exception.AppException;
import com.carenest.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * Sends SMS messages. Not implemented yet: plug in an SMS provider (eSMS, SpeedSMS, Twilio...) here.
 */
@Service
public class SmsService {

    public void sendSms(String phoneNumber, String content) {
        // TODO: call the SMS provider.
        throw new AppException(ErrorCode.SMS_NOT_SUPPORTED);
    }
}
