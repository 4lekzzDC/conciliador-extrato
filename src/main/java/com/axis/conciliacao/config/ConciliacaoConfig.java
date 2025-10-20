package com.axis.conciliacao.config;

import com.axis.conciliacao.utils.ExcelReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConciliacaoConfig {

    @Bean
    public ExcelReader excelReader() {
        return new ExcelReader(); // usa seu ExcelReader existente
    }
}
