package com.umc.i.src.review;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.umc.i.config.BaseException;
import com.umc.i.config.BaseResponseStatus;
import com.umc.i.src.feeds.model.patch.PatchFeedsReq;
import com.umc.i.src.review.model.Review;
import com.umc.i.src.review.model.get.GetAllReviewsRes;
import com.umc.i.src.review.model.patch.PatchReviewsReq;
import com.umc.i.src.review.model.post.PostReviewReq;
import com.umc.i.utils.S3Storage.Image;

import javax.sql.DataSource;

@Repository
public class ReviewDao {
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    // 장터 후기 작성
    public int createReviews(PostReviewReq postReviewReq) throws BaseException {
        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String currentTime = time.format(timeFormatter);

        try {
            String createReviewsQuery = "insert into Market_review (board_idx, sell_mem_idx, buy_mem_idx, review_goods, review_content, review_image, review_hit, review_blame, review_created_at) ";
            createReviewsQuery += "values (3, ?, ?, ?, ?, ?, 0, 0, ?)";

            Object[] createReviewsParams = new Object[] {postReviewReq.getSellerIdx(), postReviewReq.getBuyerIdx(), postReviewReq.getGoods(), postReviewReq.getContent(), postReviewReq.getImgCnt(), currentTime};
            this.jdbcTemplate.update(createReviewsQuery, createReviewsParams);  // 장터 후기 저장

            String laseInsertQuery = "select last_insert_id()"; // 가장 마지막에 삽입된 id 값 가져온다
            int reviewIdx = this.jdbcTemplate.queryForObject(laseInsertQuery, int.class);

            return reviewIdx;        // 생성된 장터 후기 인덱스
        } catch (Exception e) {
            e.printStackTrace();
            throw new BaseException(BaseResponseStatus.POST_FEEDS_UPLOAD_FAIL);
        }
    }

    // 장터 후기 이미지 정보 저장
    public void createReviewsImage(List<Image> img, int reviewIdx) {
        String createReviewsImageQuery = "insert into Image_url (content_category, content_idx, image_url, image_order)";
        createReviewsImageQuery += " values (?, ?, ?, ?)";
        for (int i = 0; i < img.size(); i++) {
            Object[] createReviewsImageParams = new Object[] {img.get(i).getCategory(), reviewIdx, img.get(i).getUploadFilePath(), i};
            this.jdbcTemplate.update(createReviewsImageQuery, createReviewsImageParams);
        }
    }


    // 장터 후기 수정
    public int editReviews(PatchReviewsReq patchReviewsReq, List<Image> img){
        // 게시글 수정
        String editReviewsQuery = "update Market_review set review_goods = ?, review_content = ?, review_image = ? where review_idx = ?";
        Object[] editReviewsParams = new Object[] {patchReviewsReq.getGoods(), patchReviewsReq.getContent(), patchReviewsReq.getImgCnt(), patchReviewsReq.getReviewIdx()};

        this.jdbcTemplate.update(editReviewsQuery, editReviewsParams);

        // 이미지 수정(삭제 후 추가)
        editReviewsQuery = "delete from Image_url where content_category = 3 && content_idx = ?";
        this.jdbcTemplate.update(editReviewsQuery, patchReviewsReq.getReviewIdx());

        if(img != null) {       // 이미지가 있으면
            editReviewsQuery = "insert into Image_url (content_category, content_idx, image_url, image_order) values (?, ?, ?, ?)";
            for (int i = 0; i < img.size(); i++) {
                Object[] editReviewsImageParams = new Object[] {3, patchReviewsReq.getReviewIdx(), img.get(i).getUploadFilePath(), i};
                this.jdbcTemplate.update(editReviewsQuery, editReviewsImageParams);
            }
        }

        return patchReviewsReq.getReviewIdx();
    }

    // 이미지 조회
    public List<Image> getReviewsImage(int reviewIdx) {
        String getReviewsImageQuery = "select * from Image_url where content_category = 3 && content_idx = ?";
        
        return this.jdbcTemplate.query(getReviewsImageQuery, 
        (rs, rowNum) -> new Image(
            rs.getString("image_url"), 
            rs.getString("image_url"),
            rs.getInt("content_category"), 
            rs.getInt("content_idx")), 
            reviewIdx);
    }

    // 장터 후기 삭제
    public void delteReview(int reviewIdx) {
        try {
            String deleteReviewQuery = "delete from Market_review where review_idx = ?";
            this.jdbcTemplate.update(deleteReviewQuery, reviewIdx);

            deleteReviewQuery = "delete from Image_url where content_category = 3 && content_idx = ?";
            this.jdbcTemplate.update(deleteReviewQuery, reviewIdx);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }

    // 장터 후기 상세 조회
    public Review getReview(int reviewIdx) {
        String getReviewQuery = "update Market_review set review_hit = review_hit + 1";
        this.jdbcTemplate.update(getReviewQuery);

        getReviewQuery = "select review_idx, sell_mem_idx, A.mem_nickname as seller_nick, buy_mem_idx, B.mem_nickname as buyer_nick, review_goods, review_content, review_hit, review_created_at";
        getReviewQuery += " from Market_review, Member A, Member B where review_idx = ? && sell_mem_idx = A.mem_idx && buy_mem_idx = B.mem_idx";
    
        return this.jdbcTemplate.queryForObject(getReviewQuery, 
        (rs, rowNum) -> new Review(
            rs.getInt("review_idx"),
            rs.getInt("buy_mem_idx"),
            rs.getInt("sell_mem_idx"),
            rs.getString("buyer_nick"),
            rs.getString("seller_nick"),
            rs.getString("review_goods"),
            rs.getString("review_content"),
            rs.getInt("review_hit"),
            rs.getString("review_created_at")), 
            reviewIdx);
    }
    
    
    // 장터후기 전체 조회
    public List<GetAllReviewsRes> getAllReviews() {
        String getAllReviewQuery = "select review_idx, sell_mem_idx, A.mem_nickname as seller_nick, buy_mem_idx, B.mem_nickname as buyer_nick, ";
        getAllReviewQuery += " I.Market_review.review_goods, review_content, review_hit, review_created_at ";
        getAllReviewQuery += " from Market_review, Member A, Member B";
        getAllReviewQuery += " where Market_review.sell_mem_idx = A.mem_idx && Market_review.buy_mem_idx = B.mem_idx";
        getAllReviewQuery += " order by review_created_at desc limit 20 offset 0";
    
        return this.jdbcTemplate.query(getAllReviewQuery, 
        (rs, rowNum) -> new GetAllReviewsRes(
                rs.getInt("review_idx"),
                rs.getInt("buy_mem_idx"),
                rs.getInt("sell_mem_idx"),
                rs.getString("buyer_nick"),
                rs.getString("seller_nick"),
                rs.getString("mem_profile_url"),
                rs.getString("review_goods"),
                rs.getInt("review_hit"),
                rs.getInt("comment_cnt"),
                rs.getInt("likeCnt"),
                rs.getString("review_created_at"),
                rs.getString("review_image")));
    }
}
