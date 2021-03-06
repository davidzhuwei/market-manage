package cn.lmjia.market.core.service.impl;

import cn.lmjia.market.core.config.CoreConfig;
import cn.lmjia.market.core.entity.MainOrder;
import cn.lmjia.market.core.entity.trj.AuthorisingInfo;
import cn.lmjia.market.core.service.PayAssistanceService;
import cn.lmjia.market.core.service.PayService;
import cn.lmjia.market.core.trj.InvalidAuthorisingException;
import cn.lmjia.market.core.trj.TRJService;
import me.jiangcai.payment.PayableOrder;
import me.jiangcai.payment.PaymentConfig;
import me.jiangcai.payment.chanpay.service.ChanpayPaymentForm;
import me.jiangcai.payment.exception.SystemMaintainException;
import me.jiangcai.payment.hua.huabei.HuabeiPaymentForm;
import me.jiangcai.payment.paymax.PaymaxChannel;
import me.jiangcai.payment.paymax.PaymaxPaymentForm;
import me.jiangcai.payment.service.PaymentService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author CJ
 */
@Service
public class PayAssistanceServiceImpl implements PayAssistanceService {

    private static final Log log = LogFactory.getLog(PayAssistanceServiceImpl.class);
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    @Autowired
    private PayService payService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ChanpayPaymentForm chanpayPaymentForm;
    @Autowired
    private PaymaxPaymentForm paymaxPaymentForm;
    @Autowired
    private HuabeiPaymentForm huabeiPaymentForm;
    @Autowired
    private Environment environment;
    @Autowired
    private TRJService trjService;

    @PreDestroy
    public void close() {
        executorService.shutdown();
    }

    @Override
    public ModelAndView payOrder(String openId, HttpServletRequest request, PayableOrder order, boolean huabei)
            throws SystemMaintainException {
        if (payService.isPaySuccess(order.getPayableOrderId().toString())) {
            if (order instanceof MainOrder)
                return new ModelAndView("redirect:"
                        + payService.mainOrderPaySuccessUri(request, (MainOrder) order, null));
            throw new IllegalStateException("订单并不在待支付状态: for:" + order);
        }

        if (!huabei && environment.acceptsProfiles("autoPay")) {
            // 3 秒之后自动付款
            if (new Random().nextBoolean()) {
                log.warn("3秒之后自动付款:" + order);
                executorService.schedule(()
                                -> paymentService.mockPay(order)
                        , 3, TimeUnit.SECONDS);
            } else
                log.warn("任性了，就是不付款。");
        }

        if (environment.acceptsProfiles("wechatChanpay"))
            return paymentService.startPay(request, order, chanpayPaymentForm, null);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("channel", PaymaxChannel.wechat);
        // 单元测试的时候 无法建立公众号付款
        if (environment.acceptsProfiles(CoreConfig.ProfileUnitTest, "wechatScanOnly")) {
            parameters.put("channel", PaymaxChannel.wechatScan);
        }
        parameters.put("openId", openId);
        parameters.put(HuabeiPaymentForm.PERIODS, 24);
        if (huabei)
            return paymentService.startPay(request, order, huabeiPaymentForm, parameters);
        return paymentService.startPay(request, order, paymaxPaymentForm, parameters);
    }

    @Override
    public ModelAndView payOrder(String openId, HttpServletRequest request, PayableOrder order, String authorising
            , String idNumber) throws SystemMaintainException, InvalidAuthorisingException {
        AuthorisingInfo info = trjService.checkAuthorising(authorising, idNumber);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("info", info);
        parameters.put(PaymentConfig.SKIP_TEST_PARAMETER_NAME, true);
        return paymentService.startPay(request, order, trjService, parameters);
    }

    @Override
    public void checkAuthorising(String authorising, String idNumber) throws InvalidAuthorisingException {
        trjService.checkAuthorising(authorising, idNumber);
    }
}
