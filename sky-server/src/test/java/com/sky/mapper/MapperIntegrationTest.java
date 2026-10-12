package com.sky.mapper;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Dish;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.service.impl.OrderServiceImpl;
import com.sky.service.impl.SetmealServiceimpl;
import com.sky.service.OrderPricingService;
import com.sky.service.ShopService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Executes the real mapper SQL against an isolated database; no development data is touched. */
class MapperIntegrationTest {
    private JdbcDataSource dataSource;
    private SqlSessionTemplate session;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER");
        Configuration configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeAliasRegistry().registerAliases("com.sky.entity");
        for (String mapper : Arrays.asList("DishMapper", "UserMapper", "OrderMapper", "SetmealMapper",
                "SetmealDishMapper", "AddressBookMapper", "OrderDetailMapper", "ShoppingCartMapper")) {
            String path = "mapper/" + mapper + ".xml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                new XMLMapperBuilder(in, configuration, path, configuration.getSqlFragments()).parse();
            }
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        session = new SqlSessionTemplate(factory);
        execute(
                "create table user (id bigint primary key, create_time timestamp)",
                "create table dish (id bigint primary key, name varchar(100), category_id bigint, status int, create_time timestamp, price decimal(10,2), image varchar(100))",
                "create table setmeal (id bigint primary key, status int, name varchar(100), create_time timestamp, create_user bigint, update_time timestamp, update_user bigint)",
                "create table setmeal_dish (id bigint primary key, setmeal_id bigint, dish_id bigint)",
                "create table orders (id bigint auto_increment primary key, number varchar(100), status int, user_id bigint, " +
                        "address_book_id bigint, order_time timestamp, checkout_time timestamp, pay_method int, pay_status int, " +
                        "amount decimal(10,2), remark varchar(100), phone varchar(20), address varchar(200), consignee varchar(50), " +
                        "estimated_delivery_time timestamp, delivery_status int, pack_amount int, tableware_number int, tableware_status int, " +
                        "cancel_reason varchar(100), rejection_reason varchar(100), cancel_time timestamp, delivery_time timestamp)",
                "create table order_detail (id bigint auto_increment primary key, name varchar(100) not null, image varchar(100), " +
                        "order_id bigint, dish_id bigint, setmeal_id bigint, dish_flavor varchar(100), number int, amount decimal(10,2))",
                "create table address_book (id bigint primary key, user_id bigint, consignee varchar(50), phone varchar(20), detail varchar(100))",
                "create table shopping_cart (id bigint primary key, user_id bigint, dish_id bigint, setmeal_id bigint, " +
                        "name varchar(100), image varchar(100), dish_flavor varchar(100), number int, amount decimal(10,2), create_time timestamp)"
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        BaseContext.removeCurrentId();
        execute("shutdown");
    }

    private void execute(String... statements) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private int count(String table) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select count(*) from " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private <T> T transactional(T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    @Test
    void dishListWorksWithAndWithoutFilters() throws Exception {
        execute("insert into dish (id, name, category_id, status, create_time) values (1, '米饭', 1, 1, '2026-10-12 10:00:00')",
                "insert into dish (id, name, category_id, status, create_time) values (2, '面条', 2, 0, '2026-10-12 11:00:00')");
        DishMapper mapper = session.getMapper(DishMapper.class);
        assertEquals(2, mapper.list(new Dish()).size());
        assertEquals(2L, mapper.list(new Dish()).get(0).getId());
        assertEquals(1L, mapper.list(Dish.builder().name("米").categoryId(1L).status(1).build()).get(0).getId());
    }

    @Test
    void setmealResolvesItsDishRatherThanDishWithSameIdAsSetmeal() throws Exception {
        execute("insert into dish (id, name, status) values (10, '关联菜品', 0), (20, '无关菜品', 1)",
                "insert into setmeal_dish values (1, 20, 10)");
        assertEquals(10L, session.getMapper(DishMapper.class).getBySetmealId(20L).get(0).getId());
    }

    @Test
    void userCountsUseCreateTimeAndIncludeStartOfDay() throws Exception {
        execute("insert into user values (1, '2026-10-11 10:00:00'), (2, '2026-10-12 00:00:00'), (3, '2026-10-13 00:00:00')");
        Map<String, Object> range = new HashMap<>();
        range.put("begin", LocalDateTime.of(2026, 10, 12, 0, 0));
        range.put("end", LocalDateTime.of(2026, 10, 12, 23, 59, 59));
        assertEquals(1, session.getMapper(UserMapper.class).countByMap(range));
    }

    @Test
    void namedOrderParametersBindForTimeoutAndSalesQueries() throws Exception {
        execute("insert into orders (id, status, order_time, amount) values " +
                        "(1, 1, '2026-10-11 12:00:00', 10), (2, 5, '2026-10-12 00:00:00', 20), (3, 5, '2026-10-10 10:00:00', 30)",
                "insert into order_detail (name, order_id, number) values ('米饭', 2, 2), ('面条', 3, 100)");
        OrderMapper mapper = session.getMapper(OrderMapper.class);
        LocalDateTime begin = LocalDateTime.of(2026, 10, 12, 0, 0);
        assertEquals(1L, mapper.getByStatusAndOrderTimeLT(1, begin).get(0).getId());
        assertEquals("米饭", mapper.getSalesTop10(begin, begin.plusDays(1)).get(0).getName());
        Map<String, Object> range = new HashMap<>();
        range.put("begin", begin);
        range.put("end", begin.plusDays(1));
        range.put("status", 5);
        assertEquals(1, mapper.countByMap(range));
        assertEquals(20.0, mapper.sumByMap(range));
    }

    @Test
    void setmealUpdatePreservesCreationAuditAndUpdatesModificationAudit() throws Exception {
        execute("insert into setmeal values (1, 0, '原套餐', '2026-10-10 00:00:00', 1, '2026-10-10 00:00:00', 1)");
        LocalDateTime updateTime = LocalDateTime.of(2026, 10, 12, 12, 0);
        SetmealMapper mapper = session.getMapper(SetmealMapper.class);
        mapper.update(Setmeal.builder().id(1L).name("新套餐").updateUser(2L).updateTime(updateTime).build());
        Setmeal updated = mapper.getById(1L);
        assertEquals(1L, updated.getCreateUser());
        assertEquals(2L, updated.getUpdateUser());
        assertEquals(updateTime, updated.getUpdateTime());
    }

    @Test
    void failedDetailInsertRollsBackOrderAndPreservesCart() throws Exception {
        execute("insert into address_book values (2, 1, '张三', '13800000000', '文一路1号')",
                "insert into shopping_cart (id, user_id, dish_id, number, amount) values (1, 1, 10, 1, 38.50)",
                "insert into dish (id, status, price) values (10, 1, 38.50)");
        OrderServiceImpl target = new OrderServiceImpl();
        ReflectionTestUtils.setField(target, "orderMapper", session.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(target, "addressBookMapper", session.getMapper(AddressBookMapper.class));
        ReflectionTestUtils.setField(target, "shoppingCartMapper", session.getMapper(ShoppingCartMapper.class));
        ReflectionTestUtils.setField(target, "orderDetailMapper", session.getMapper(OrderDetailMapper.class));
        ReflectionTestUtils.setField(target, "orderPricingService", new OrderPricingService(
                session.getMapper(DishMapper.class), session.getMapper(SetmealMapper.class), 2));
        ReflectionTestUtils.setField(target, "shopService", mock(ShopService.class));
        OrderServiceImpl service = transactional(target);
        BaseContext.setCurrentId(1L);
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(2L);
        dto.setAmount(new BigDecimal("38.50"));

        // The current dish name is null, so the detail table's NOT NULL constraint rejects the insert.
        assertThrows(DataIntegrityViolationException.class, () -> service.orderSubmit(dto));
        assertEquals(0, count("orders"));
        assertEquals(1, count("shopping_cart"));
    }

    @Test
    void databaseFailureRollsBackEntireSetmealBatch() throws Exception {
        execute("insert into setmeal (id, status) values (1, 0), (2, 0)",
                "insert into setmeal_dish values (1, 1, 10), (2, 2, 20)",
                "create table setmeal_ref (setmeal_id bigint references setmeal(id))",
                "insert into setmeal_ref values (2)");
        SetmealServiceimpl target = new SetmealServiceimpl();
        ReflectionTestUtils.setField(target, "setmealMapper", session.getMapper(SetmealMapper.class));
        ReflectionTestUtils.setField(target, "setmealDishMapper", session.getMapper(SetmealDishMapper.class));
        SetmealServiceimpl service = transactional(target);

        assertThrows(DataIntegrityViolationException.class, () -> service.deleteBatch(Arrays.asList(1L, 2L)));
        assertEquals(2, count("setmeal"));
        assertEquals(2, count("setmeal_dish"));
    }
}
