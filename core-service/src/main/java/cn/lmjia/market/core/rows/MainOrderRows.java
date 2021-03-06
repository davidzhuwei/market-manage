package cn.lmjia.market.core.rows;

import cn.lmjia.market.core.entity.Customer;
import cn.lmjia.market.core.entity.Customer_;
import cn.lmjia.market.core.entity.Login;
import cn.lmjia.market.core.entity.MainOrder;
import cn.lmjia.market.core.entity.MainOrder_;
import cn.lmjia.market.core.entity.support.OrderStatus;
import cn.lmjia.market.core.entity.trj.TRJPayOrder;
import cn.lmjia.market.core.row.FieldDefinition;
import cn.lmjia.market.core.row.field.FieldBuilder;
import cn.lmjia.market.core.row.field.Fields;
import cn.lmjia.market.core.service.ReadService;
import me.jiangcai.payment.chanpay.entity.ChanpayPayOrder;
import me.jiangcai.payment.entity.PayOrder;
import me.jiangcai.payment.hua.huabei.entity.HuaHuabeiPayOrder;
import me.jiangcai.payment.paymax.entity.PaymaxPayOrder;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 展示订单数据的格式
 *
 * @author CJ
 */
public abstract class MainOrderRows extends AbstractRows<MainOrder> {

    /**
     * 要渲染这些记录的身份
     */
    private final Login login;

    public MainOrderRows(Login login, Function<LocalDateTime, String> localDateTimeFormatter) {
        super(localDateTimeFormatter);
        this.login = login;
    }

    @Override
    public List<Order> defaultOrder(CriteriaBuilder criteriaBuilder, Root<MainOrder> root) {
        return Collections.singletonList(criteriaBuilder.desc(root.get("orderTime")));
    }

    @Override
    public Class<MainOrder> entityClass() {
        return MainOrder.class;
    }

    @Override
    public List<FieldDefinition<MainOrder>> fields() {
        return Arrays.asList(
                Fields.asBasic("id")
                , Fields.asBasic("orderBody")
                , Fields.asBiFunction("user", ((root, criteriaBuilder)
                        -> ReadService.nameForLogin(MainOrder.getOrderByLogin(root)
                        , criteriaBuilder)))
                , Fields.asBiFunction("userLevel", ((root, criteriaBuilder)
                        -> ReadService.agentLevelForLogin(MainOrder.getOrderByLogin(root)
                        , criteriaBuilder)))
                , Fields.asBiFunction("orderId", MainOrder::getSerialId)
                , Fields.asFunction("orderUser", ((root)
                        -> root.get(MainOrder_.customer).get(Customer_.name)))
                , Fields.asBiFunction("phone", (root, criteriaBuilder)
                        -> Customer.getMobile(MainOrder.getCustomer(root)))
//                , Fields.asFunction("category", root -> root.get(MainOrder_.good).get(MainGood_.product).get(Product_.mainCategory))
//                , Fields.asFunction("goods", root -> root.get(MainOrder_.good).get(MainGood_.product).get(Product_.name))
//                , Fields.asFunction("type", root -> root.get(MainOrder_.good).get(MainGood_.product).get(Product_.code))
//                , FieldBuilder.asName(MainOrder.class, "amount")
//                        .addSelect(root -> root.get(MainOrder_.amount))
//                        .build()
                , Fields.asBiFunction("package", ((root, criteriaBuilder)
                        -> criteriaBuilder.literal("")))
                , FieldBuilder.asName(MainOrder.class, "method")
                        .addBiSelect(((root, criteriaBuilder) -> root.join("payOrder", JoinType.LEFT)))
                        .addFormat((data, type) -> {
                            PayOrder x = (PayOrder) data;
                            if (x == null)
                                return "无";
                            if (x instanceof PaymaxPayOrder)
                                return "拉卡拉";
                            if (x instanceof ChanpayPayOrder)
                                return "畅捷";
                            if (x instanceof TRJPayOrder)
                                return "投融家";
                            if (x instanceof HuaHuabeiPayOrder)
                                return "花呗";
                            return "未知";
                        })
                        .build()
                , FieldBuilder.asName(MainOrder.class, "methodCode")
                        .addBiSelect(((root, criteriaBuilder) -> root.join("payOrder", JoinType.LEFT)))
                        .addFormat((data, type) -> {
                            PayOrder x = (PayOrder) data;
                            if (x == null)
                                return 0;
                            if (x instanceof PaymaxPayOrder)
                                return 1;
                            if (x instanceof ChanpayPayOrder)
                                return 4;
                            if (x instanceof TRJPayOrder)
                                return 2;
                            if (x instanceof HuaHuabeiPayOrder)
                                return 3;
                            return 99;
                        })
                        .build()
                , Fields.asBiFunction("total", (MainOrder::getOrderDueAmount))
//                , Fields.asFunction("address", root -> root.get("installAddress"))
                , FieldBuilder.asName(MainOrder.class, "address")
                        .addSelect(root -> root.get("installAddress"))
                        .addFormat((object, type) -> object.toString())
                        .build()
                , FieldBuilder.asName(MainOrder.class, "status")
                        .addSelect(root -> root.get("orderStatus"))
                        .addFormat((data, type) -> data == null ? null : data.toString())
                        .build()
                , FieldBuilder.asName(MainOrder.class, "statusCode")
                        .addSelect(root -> root.get("orderStatus"))
                        .addFormat((data, type) -> data == null ? null : ((Enum) data).ordinal())
                        .build()
                , getOrderTime()
                , FieldBuilder.asName(MainOrder.class, "quickDoneAble")
                        .addSelect(root -> root.get("orderStatus"))
                        .addFormat((data, type) -> {
                            OrderStatus orderStatus = (OrderStatus) data;
                            return login.isManageable() && orderStatus == OrderStatus.forDeliver;
                        })
                        .build()
        );
    }

    protected FieldDefinition<MainOrder> getOrderTime() {
        return FieldBuilder.asName(MainOrder.class, "orderTime")
                .addFormat((data, type)
                        -> localDateTimeFormatter.apply(((LocalDateTime) data)))
                .build();
    }
}
