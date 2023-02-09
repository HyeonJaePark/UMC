package com.umc.i.src.review.model.patch;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatchReviewsDeleteReq {
    private int reviewIdx;      // 장터 후기 인덱스
}
