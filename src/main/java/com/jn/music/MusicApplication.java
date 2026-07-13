package com.jn.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@MapperScan("com.jn.music.**.mapper")
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
public class MusicApplication {
	//flac压缩
	//https://www.compresss.com/cn/compress-audio.html
	//音频聚合
	//https://dh.89729981.xyz/
	//https://xiageba.liumingye.cn/

	public static void main(String[] args) {
		SpringApplication.run(MusicApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
		return interceptor;
	}
	
}
