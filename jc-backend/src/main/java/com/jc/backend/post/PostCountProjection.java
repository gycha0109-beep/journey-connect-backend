package com.jc.backend.post;

/** 게시물 페이지에서 사용할 좋아요·북마크 집계 결과입니다. */
public interface PostCountProjection {

    Long getPostId();

    long getTotal();
}
