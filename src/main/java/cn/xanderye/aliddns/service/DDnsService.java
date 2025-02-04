package cn.xanderye.aliddns.service;

import cn.xanderye.aliddns.util.HttpUtil;
import cn.xanderye.aliddns.util.PropertyUtil;
import cn.xanderye.aliddns.util.StringUtil;
import cn.xanderye.aliddns.util.SystemUtil;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsRequest;
import com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordRequest;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.DefaultProfile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 2020/7/9.
 *
 * @author XanderYe
 */
@Slf4j
public class DDnsService {

    private static String regionId;
    private static String accessKeyId;
    private static String accessSecret;
    private static String rr;
    private static String domainName;

    private static IAcsClient client;

    static {
        PropertyUtil.init();
        regionId = SystemUtil.getOrDefault("REGIN_ID", PropertyUtil.get("aliyun.region-id"));
        accessKeyId = SystemUtil.getOrDefault("ACCESS_KEY_ID", PropertyUtil.get("aliyun.access-key-id"));
        accessSecret = SystemUtil.getOrDefault("ACCESS_SECRET", PropertyUtil.get("aliyun.access-secret"));
        rr = SystemUtil.getOrDefault("RR", PropertyUtil.get("aliyun.rr"));
        domainName = SystemUtil.getOrDefault("DOMAIN_NAME", PropertyUtil.get("aliyun.domainName"));
        if (StringUtil.isAnyEmpty(regionId, accessKeyId, accessSecret, rr, domainName)) {
            log.error("请先配置参数");
            System.exit(-1);
        }
        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessSecret);
        client = new DefaultAcsClient(profile);
    }

    //private static DescribeDomainRecordsResponse.Record cacheRecord = null;

    public void ddns() {
        String ip = getIp();
        changeRecord(ip);
    }

    public String getIp() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Origin", "http://ip.taobao.com");
        headers.put("Referer", "http://ip.taobao.com/");
        Map<String, Object> params = new HashMap<>();
        params.put("ip", "myip");
        params.put("accessKey", "alibaba-inc");
        try {
            String result = HttpUtil.doPost("http://ip.taobao.com/outGetIpInfo", headers, null, params);

            return StringUtil.substringBetween(result, "queryIp\":\"", "\",");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 查询并判断更改记录
     * @param value
     * @return void
     * @author XanderYe
     * @date 2020/7/9
     */
    public void changeRecord(String value) {
        if (domainName == null || "".equals(domainName)) {
            log.error("请配置域名");
            return;
        }
        if (rr == null || "".equals(rr)) {
            log.error("请配置主机记录");
            return;
        }
        log.info("拉取解析记录");
        List<DescribeDomainRecordsResponse.Record> recordList = getDescribeDomainRecords(domainName);
        if (recordList != null && !recordList.isEmpty()) {
            String[] rrList = rr.split(",");
            for (String string : rrList){
                for (DescribeDomainRecordsResponse.Record record : recordList) {
                    if (string.equals(record.getRR())) {
                        if (!record.getValue().equals(value)) {
                            log.info("记录值变动，开始更新");
                            record.setType("A");
                            record.setValue(value);
                            boolean bool = updateDomainRecord(record);
                            if (bool) {
                                log.info("主机记录为" + rr + "的记录值更新为：" + value);
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * 查询记录
     *
     * @param domainName
     * @return java.util.List<com.aliyuncs.alidns.model.v20150109.DescribeDomainRecordsResponse.Record>
     * @author XanderYe
     * @date 2020/7/9
     */
    public List<DescribeDomainRecordsResponse.Record> getDescribeDomainRecords(String domainName) {
        DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
        request.setSysRegionId(regionId);
        request.setDomainName(domainName);
        request.setPageNumber(1L);
        request.setPageSize(10L);
        try {
            DescribeDomainRecordsResponse response = client.getAcsResponse(request);
            return response.getDomainRecords();
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
        }
        return null;
    }

    /**
     * 更新记录
     *
     * @param record
     * @return boolean
     * @author XanderYe
     * @date 2020/7/9
     */
    public boolean updateDomainRecord(DescribeDomainRecordsResponse.Record record) {
        UpdateDomainRecordRequest request = new UpdateDomainRecordRequest();
        request.setSysRegionId(regionId);
        request.setRecordId(record.getRecordId());
        request.setRR(record.getRR());
        request.setType(record.getType());
        request.setValue(record.getValue());
        try {
            UpdateDomainRecordResponse response = client.getAcsResponse(request);
            return true;
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
        }
        return false;
    }

    public static String recordToString(DescribeDomainRecordsResponse.Record record) {
        return "主机记录=" + record.getRR() + "，解析线路=" + record.getLine()
                + "，当前的解析记录状态=" + record.getStatus() + "，当前解析记录锁定状态=" + record.getLocked()
                + "，记录类型=" + record.getType() + "，域名名称=" + record.getDomainName()
                + "，记录值=" + record.getValue() + "，解析记录ID=" + record.getRecordId()
                + "，生存时间=" + record.getTTL() + "，负载均衡权重=" + record.getWeight();
    }

}
