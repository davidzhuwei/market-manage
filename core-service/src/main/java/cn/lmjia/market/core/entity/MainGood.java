package cn.lmjia.market.core.entity;

import cn.lmjia.market.core.entity.channel.Channel;
import cn.lmjia.market.core.entity.channel.InstallmentChannel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 具体的商品，用以销售
 *
 * @author CJ
 */
@Entity
@Setter
@Getter
public class MainGood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 上架状态
     */
    private boolean enable;
    @ManyToOne
    private MainProduct product;
    @ManyToOne
    private Channel channel;
    @Column(columnDefinition = "timestamp")
    private LocalDateTime createTime = LocalDateTime.now();
    /**
     * 该货品是否可以成为佣金的源头
     *
     * @since {@link cn.lmjia.market.core.Version#newCommission}
     */
    private boolean commissionSource;
    /**
     * 标签，它应该是一个多对多的关联
     */
//    @Convert(converter = GoodTagConverter.class)
    @ManyToMany(cascade = {CascadeType.REFRESH})
    private Set<Tag> tags;

    /**
     * @param path
     * @param criteriaBuilder
     * @return
     * @see {@link MainGood#getTotalPrice()}
     */
    public static Expression<BigDecimal> getTotalPrice(From<?, MainGood> path, CriteriaBuilder criteriaBuilder) {
        final Path<MainProduct> product = path.get(MainGood_.product);
        Join<MainGood, Channel> channel = path.join(MainGood_.channel, JoinType.LEFT);

        // deposit+install
        final Expression<Number> simpleSum = criteriaBuilder.sum(product.get(MainProduct_.deposit), product.get(MainProduct_.install));
        // 这个情况下 价格等于 deposit*depositRate*(1+poundageRate)+ install
        final Expression<Number> installmentChannelSum = criteriaBuilder.sum(
                criteriaBuilder.prod(
                        criteriaBuilder.prod(product.get("deposit")
                                , channel.get("depositRate"))
                        , criteriaBuilder.sum(criteriaBuilder.treat(channel
                                , InstallmentChannel.class).get("poundageRate")
                                , criteriaBuilder.literal(1))
                )
                , product.get("install")
        );
        // deposit*dRate+ install
        final Expression<Number> otherChannelSum = criteriaBuilder.sum(criteriaBuilder.prod(product.get("deposit")
                , channel.get("depositRate"))
                , product.get("install")
        );
        return criteriaBuilder.toBigDecimal(
                criteriaBuilder.<Boolean, Number>selectCase(channel.isNull())
                        .when(true
                                , simpleSum)
                        .otherwise(
                                criteriaBuilder.<String, Number>selectCase(channel.get("classType").as(String.class))
                                        .when("InstallmentChannel",
                                                installmentChannelSum
                                        )
                                        .otherwise(
                                                otherChannelSum
                                        )
                        )
        );
    }

    /**
     * @return 渠道所带来的溢价或者优惠
     */
    public BigDecimal getChannelChangeAsAdd() {
        return getTotalPrice().subtract(product.getDeposit()).subtract(product.getInstall());
    }

    /**
     * @return 总价
     * @see {@link MainGood#getTotalPrice(From, CriteriaBuilder)}
     */
    public BigDecimal getTotalPrice() {
        BigDecimal price = product.getDeposit();
        // 如果是特定渠道的
        if (channel != null) {
            price = price.multiply(channel.getDepositRate());
            if (channel instanceof InstallmentChannel) {
                price = price.add(price.multiply(((InstallmentChannel) channel).getPoundageRate()));
            }
        }
        return price.add(product.getInstall());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MainGood)) return false;
        MainGood good = (MainGood) o;
        return Objects.equals(id, good.id) &&
                Objects.equals(product, good.product) &&
                Objects.equals(channel, good.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, product, channel);
    }
}
