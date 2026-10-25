package ru.kondrashov.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;
import ru.kondrashov.accountservice.controllers.AccountsController;
import ru.kondrashov.accountservice.controllers.BillsController;
import ru.kondrashov.accountservice.controllers.SwaggerController;
import ru.kondrashov.accountservice.services.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionBoundaryConventionTest {

    private static final List<Class<?>> SERVICES = Arrays.asList(
            AccountsServiceImpl.class,
            BillServiceImpl.class,
            AdjustmentsServiceImpl.class,
            PaymentsServiceImpl.class,
            TransferServiceImpl.class);

    private static final List<Class<?>> CONTROLLERS = Arrays.asList(
            AccountsController.class,
            BillsController.class,
            SwaggerController.class);

    private final AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

    @Test
    void serviceWriteMethodsRunInReadWriteTransactionsThatRollBackOnAnyException() {
        SERVICES.forEach(service -> publicMethods(service)
                .filter(method -> method.getReturnType() == void.class)
                .forEach(method -> {
                    TransactionAttribute attribute = attributes.getTransactionAttribute(method, service);
                    assertThat(attribute).as(name(method)).isNotNull();
                    assertThat(attribute.isReadOnly()).as(name(method)).isFalse();
                    assertThat(attribute.rollbackOn(new Exception())).as(name(method)).isTrue();
                }));
    }

    @Test
    void serviceReadMethodsRunInReadOnlyTransactions() {
        SERVICES.forEach(service -> publicMethods(service)
                .filter(method -> method.getReturnType() != void.class)
                .forEach(method -> {
                    TransactionAttribute attribute = attributes.getTransactionAttribute(method, service);
                    assertThat(attribute).as(name(method)).isNotNull();
                    assertThat(attribute.isReadOnly()).as(name(method)).isTrue();
                }));
    }

    @Test
    void controllersDoNotDeclareTransactions() {
        CONTROLLERS.forEach(controller -> {
            assertThat(AnnotatedElementUtils.hasAnnotation(controller, Transactional.class)).as(controller.getSimpleName()).isFalse();
            List<String> transactional = Arrays.stream(controller.getDeclaredMethods())
                    .filter(method -> AnnotatedElementUtils.hasAnnotation(method, Transactional.class))
                    .map(this::name)
                    .collect(Collectors.toList());
            assertThat(transactional).isEmpty();
        });
    }

    private Stream<Method> publicMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isSynthetic());
    }

    private String name(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
