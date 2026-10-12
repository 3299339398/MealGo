package com.sky.aspect;

import com.sky.annotation.AutoFill;
//import jdk.internal.classfile.MethodSignature;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * org.aspectj.lang.reflect.MethodSignature用于在 AOP 切面中获取被拦截方法的运行时元数据，例如：
 * 方法名、返回值类型、参数类型
 * 方法上的注解（如 @AutoFill）
 * 方法所在的类信息等
 * 典型场景：在 @Before @After 等通知中，通过 JoinPoint.getSignature() 转换为该类型，进而解析方法详情。
 * jdk.internal.classfile.MethodSignature用于在编译期或类文件解析时操作方法的签名结构（如字节码层面的方法描述符），属于底层类文件处理工具。例如：解析 .class 文件中方法的参数类型、返回值类型的字节码表示，通常用于开发字节码生成工具、类解析工具等。由于是 JDK 内部 API，不建议在应用程序中直接使用
 */

@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..))&& @annotation(com.sky.annotation.AutoFill)")
    public void AutoFillPointCut(){}
    //joinPoint连接点对象，通过它可以获取被拦截方法的信息（如目标对象、方法参数、方法名等

    /**
     * 前置通知
     * @param joinPoint
     */
    @Before("AutoFillPointCut()")
    public void AutoFillBefore(JoinPoint joinPoint){
        log.info("开始进行公告字段填充....");
        MethodSignature signature= (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill=signature.getMethod().getAnnotation(AutoFill.class);//获得方法上的注解对象
        OperationType operationType=autoFill.value();//获取数据库操作类型

        Object[] args= joinPoint.getArgs();
        if(args==null||args.length==0){
            return;
        }
        Object entity=args[0];
        //准备需要赋值的数据
        LocalDateTime now=LocalDateTime.now();
        Long currentId= BaseContext.getCurrentId();
        //根据不同操作类型，为响应的属性通过反射来赋值
        if(operationType.equals(OperationType.INSERT)){
            //为四个公共字段来赋值
            try {
                Method setCreatTime=entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME,LocalDateTime.class);
                Method setCreatUser=entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER,Long.class);
                Method setUpdateTime=entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser=entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                setCreatTime.invoke(entity,now);
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);
                setCreatUser.invoke(entity,currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }else if(operationType.equals(OperationType.UPDATE)){
            //两个


            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser=entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER,Long.class);

                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }


        }
    }
}
