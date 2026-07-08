package com.jn.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@MapperScan("com.jn.music.**.mapper")
@SpringBootApplication
public class MusicApplication {
	//flac压缩
	//https://www.compresss.com/cn/compress-audio.html
	//音频聚合
	//https://dh.89729981.xyz/

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
