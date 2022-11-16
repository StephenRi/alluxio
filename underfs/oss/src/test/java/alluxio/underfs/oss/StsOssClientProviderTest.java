/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.underfs.oss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import alluxio.conf.Configuration;
import alluxio.conf.InstancedConfiguration;
import alluxio.conf.PropertyKey;
import alluxio.underfs.UnderFileSystemConfiguration;
import alluxio.util.network.HttpUtils;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.comm.DefaultServiceClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpUtils.class, OSSClient.class, OSSUnderFileSystemTest.class})
public class StsOssClientProviderTest {

  InstancedConfiguration mConf;
  private static final String ECS_RAM_ROLE = "snapshot-role-test";
  private String mEcsMetadataService;
  public static final String MOCK_ECS_META_RESPONSE = "{\n"
      + "  'AccessKeyId' : 'STS.mockAK',\n"
      + "  'AccessKeySecret' : 'mockSK',\n"
      + "  'Expiration' : '2018-04-23T09:45:05Z',\n"
      + "  'SecurityToken' : 'mockSecurityToken',\n"
      + "  'LastUpdated' : '2018-04-23T03:45:05Z',\n"
      + "  'Code' : 'Success'\n"
      + "}";

  @Before
  public void before() {
    mConf = Configuration.copyGlobal();
    mEcsMetadataService = mConf.getString(
        PropertyKey.UNDERFS_OSS_STS_ECS_METADATA_SERVICE_ENDPOINT) + ECS_RAM_ROLE;
  }

  @Test
  public void testInitAndRefresh() throws Exception {
    Date dateExpiration = toUtcDateString(System.currentTimeMillis() + 21600000);
    Date dateLastUpdated = toUtcDateString(System.currentTimeMillis());
    String expiration = toUtcString(dateExpiration);
    String lastUpdated = toUtcString(dateLastUpdated);

    mConf.set(PropertyKey.OSS_ENDPOINT_KEY, "http://oss-cn-qingdao.aliyuncs.com");
    mConf.set(PropertyKey.UNDERFS_OSS_ECS_RAM_ROLE, ECS_RAM_ROLE);
    final UnderFileSystemConfiguration ossConfiguration =
        UnderFileSystemConfiguration.defaults(mConf);

    // init
    DefaultServiceClient client = Mockito.mock(DefaultServiceClient.class);
    PowerMockito.whenNew(DefaultServiceClient.class).withAnyArguments().thenReturn(client);
    OSSClient ossClient = Mockito.mock(OSSClient.class);
    PowerMockito.whenNew(OSSClient.class).withAnyArguments().thenReturn(ossClient);
    PowerMockito.mockStatic(HttpUtils.class);
    when(HttpUtils.get(mEcsMetadataService, 10000)).thenReturn(MOCK_ECS_META_RESPONSE);
    StsOssClientProvider clientProvider = new StsOssClientProvider(ossConfiguration);

    // refresh
    String responseBodyString = "{\n"
        + "  'AccessKeyId' : 'STS.mockAK',\n"
        + "  'AccessKeySecret' : 'mockSK',\n"
        + "  'Expiration' : '" + expiration + "',\n"
        + "  'SecurityToken' : 'mockSecurityToken',\n"
        + "  'LastUpdated' : '" + lastUpdated + "',\n"
        + "  'Code' : 'Success'\n"
        + "}";
    PowerMockito.mockStatic(HttpUtils.class);
    when(HttpUtils.get(mEcsMetadataService, 10000)).thenReturn(responseBodyString);
    assertTrue(clientProvider.tokenWillExpiredAfter(0));
    clientProvider.refreshOssStsClient(ossConfiguration);
    assertFalse(clientProvider.tokenWillExpiredAfter(0));
  }

  private Date toUtcDateString(long dateInMills) throws ParseException {
    TimeZone zeroTimeZone = TimeZone.getTimeZone("ETC/GMT-0");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    sdf.setTimeZone(zeroTimeZone);
    return sdf.parse(sdf.format(new Date(dateInMills)));
  }

  private String toUtcString(Date date) {
    TimeZone zeroTimeZone = TimeZone.getTimeZone("ETC/GMT-0");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    sdf.setTimeZone(zeroTimeZone);
    return sdf.format(date);
  }
}
