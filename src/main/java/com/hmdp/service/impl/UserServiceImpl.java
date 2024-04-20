package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.ognl.Token;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 发送验证码步骤

        // 1.校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);

        // 2.如果手机号格式不正确则返回错误信息
        if (phoneInvalid){
            return Result.fail("手机号格式不正确,请重新输入!");
        }
        // 3.如果手机号正确则生成验证码,发送验证码
        String code = RandomUtil.randomNumbers(6);
        log.info("系统生成的验证码:{}",code);

        // 4.将验证码保存进Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        session.setAttribute("code",code);

        // 5.发送验证码
        log.info("验证码发送成功,验证码为:{}",code);

        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式不正确,请重新输入!");
        }

        // 2.校验验证码
        String PreCode = loginForm.getCode();

        // 从redis中获取验证码
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());

        if (code == null || !PreCode.equals(code)){
            return Result.fail("验证码不正确!");
        }

        // 3.查询用户是否存在

        User user = query().eq("phone", loginForm.getPhone()).one();

        // 4.如果不存在这新建用户
        if (user == null){
            // 保存新用户
            user = createUserWithPhone(loginForm.getPhone());
        }

        // 5.将用户保存进redis中

        // 生成UUID 简洁版
        String token = UUID.randomUUID().toString(true);

        log.info("生成的UUID为:{}",token);

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        // 将对象转换成HashMap,存入Redis中 以Hash的数据类型存入
        Map<String, Object> userHashMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 将user存入redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,userHashMap);

        // 设置过期时间为30分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //generateToken();

        // 6.返回成功结果, 并将token返回只有这样前端每次访问 才能携带token
        return Result.ok(token);
    }


    @Override
    public Result logout() {
        UserHolder.removeUser();
        return Result.ok();
    }

    /**
     * 通过手机号创建用户
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        log.info("通过手机号新创建的用户为:{}",user);
        return user;
    }


    public void generateToken() {
        String[] phoneNumbers = {
                "13456762069", "13456789001", "13456789011", "13686869696", "13688668889", "13688668890", "13688668891", "13688668892", "13688668893", "13688668894",
                "13688668895", "13688668896", "13688668897", "13688668898", "13688668899", "13688668900", "13688668901", "13688668902", "13688668903", "13688668904",
                "13688668905", "13688668906", "13688668907", "13688668908", "13688668909", "13688668910", "13688668911", "13688668912", "13688668913", "13688668914",
                "13688668915", "13688668916", "13688668917", "13688668918", "13688668919", "13688668920", "13688668921", "13688668922", "13688668923", "13688668924",
                "13688668925", "13688668926", "13688668927", "13688668928", "13688668929", "13688668930", "13688668931", "13688668932", "13688668933", "13688668934",
                "13688668935", "13688668936", "13688668937", "13688668938", "13688668939", "13688668940", "13688668941", "13688668942", "13688668943", "13688668944",
                "13688668945", "13688668946", "13688668947", "13688668948", "13688668949", "13688668950", "13688668951", "13688668952", "13688668953", "13688668954",
                "13688668955", "13688668956", "13688668957", "13688668958", "13688668959", "13688668960", "13688668961", "13688668962", "13688668963", "13688668964",
                "13688668965", "13688668966", "13688668967", "13688668968", "13688668969", "13688668970", "13688668971", "13688668972", "13688668973", "13688668974",
                "13688668975", "13688668976", "13688668977", "13688668978", "13688668979", "13688668980", "13688668981", "13688668982", "13688668983", "13688668984",
                "13688668985", "13688668986", "13688668987", "13688668988", "13688668989", "13688668990", "13688668991", "13688668992", "13688668993", "13688668994",
                "13688668995", "13688668996", "13688668997", "13688668998", "13688668999", "13688669000", "13688669001", "13688669002", "13688669003", "13688669004",
                "13688669005", "13688669006", "13688669007", "13688669008", "13688669009", "13688669010", "13688669011", "13688669012", "13688669013", "13688669014",
                "13688669015", "13688669016", "13688669017", "13688669018", "13688669019", "13688669020", "13688669021", "13688669022", "13688669023", "13688669024",
                "13688669025", "13688669026", "13688669027", "13688669028", "13688669029", "13688669030", "13688669031", "13688669032", "13688669033", "13688669034",
                "13688669035", "13688669036", "13688669037", "13688669038", "13688669039", "13688669040", "13688669041", "13688669042", "13688669043", "13688669044",
                "13688669045",  "13688669046", "13688669047", "13688669048", "13688669049", "13688669050", "13688669051", "13688669052", "13688669053", "13688669054",
                "13688669055", "13688669056", "13688669057", "13688669058", "13688669059", "13688669060", "13688669061", "13688669062", "13688669063", "13688669064",
                "13688669065", "13688669066", "13688669067", "13688669068", "13688669069", "13688669070", "13688669071", "13688669072", "13688669073", "13688669074",
                "13688669075", "13688669076", "13688669077", "13688669078", "13688669079", "13688669080", "13688669081", "13688669082", "13688669083", "13688669084",
                "13688669085", "13688669086", "13688669087", "13688669088", "13688669089", "13688669090", "13688669091", "13688669092", "13688669093", "13688669094",
                "13688669095", "13688669096", "13688669097", "13688669098", "13688669099", "13688669100", "13688669101", "13688669102", "13688669103", "13688669104",
                "13688669105", "13688669106", "13688669107", "13688669108", "13688669109", "13688669110", "13688669111", "13688669112", "13688669113", "13688669114",
                "13688669115", "13688669116", "13688669117", "13688669118", "13688669119", "13688669120", "13688669121", "13688669122", "13688669123", "13688669124",
                "13688669125", "13688669126", "13688669127", "13688669128", "13688669129", "13688669130", "13688669131", "13688669132", "13688669133", "13688669134",
                "13688669135", "13688669136", "13688669137", "13688669138", "13688669139", "13688669140", "13688669141", "13688669142", "13688669143", "13688669144",
                "13688669145", "13688669146", "13688669147", "13688669148", "13688669149", "13688669150", "13688669151", "13688669152", "13688669153", "13688669154",
                "13688669155", "13688669156", "13688669157", "13688669158", "13688669159", "13688669160", "13688669161", "13688669162", "13688669163", "13688669164",
                "13688669165", "13688669166", "13688669167", "13688669168", "13688669169", "13688669170", "13688669171", "13688669172", "13688669173", "13688669174",
                "13688669175", "13688669176", "13688669177", "13688669178", "13688669179", "13688669180", "13688669181", "13688669182", "13688669183", "13688669184",
                "13688669185", "13688669186", "13688669187", "13688669188", "13688669189", "13688669190", "13688669191", "13688669192", "13688669193", "13688669194",
                "13688669195", "13688669196", "13688669197", "13688669198", "13688669199", "13688669200", "13688669201", "13688669202", "13688669203","13688669204",
                "13688669205", "13688669206", "13688669207", "13688669208", "13688669209", "13688669210", "13688669211", "13688669212", "13688669213", "13688669214",
                "13688669215", "13688669216", "13688669217", "13688669218", "13688669219", "13688669220", "13688669221", "13688669222", "13688669223", "13688669224",
                "13688669225", "13688669226", "13688669227", "13688669228", "13688669229", "13688669230", "13688669231", "13688669232", "13688669233", "13688669234",
                "13688669235", "13688669236", "13688669237", "13688669238", "13688669239", "13688669240", "13688669241", "13688669242", "13688669243", "13688669244",
                "13688669245", "13688669246", "13688669247", "13688669248", "13688669249", "13688669250", "13688669251", "13688669252", "13688669253", "13688669254",
                "13688669255", "13688669256", "13688669257", "13688669258", "13688669259", "13688669260", "13688669261", "13688669262", "13688669263", "13688669264",
                "13688669265", "13688669266", "13688669267", "13688669268", "13688669269", "13688669270", "13688669271", "13688669272", "13688669273", "13688669274",
                "13688669275", "13688669276", "13688669277", "13688669278", "13688669279", "13688669280", "13688669281", "13688669282", "13688669283", "13688669284",
                "13688669285", "13688669286", "13688669287", "13688669288", "13688669289", "13688669290", "13688669291", "13688669292", "13688669293", "13688669294",
                "13688669295", "13688669296", "13688669297", "13688669298", "13688669299", "13688669300", "13688669301", "13688669302", "13688669303", "13688669304",
                "13688669305", "13688669306", "13688669307", "13688669308", "13688669309", "13688669310", "13688669311", "13688669312", "13688669313", "13688669314",
                "13688669315", "13688669316", "13688669317", "13688669318", "13688669319", "13688669320", "13688669321", "13688669322", "13688669323", "13688669324",
                "13688669325", "13688669326", "13688669327", "13688669328", "13688669329", "13688669330", "13688669331", "13688669332", "13688669333", "13688669334",
                "13688669335", "13688669336", "13688669337", "13688669338", "13688669339", "13688669340", "13688669341", "13688669342", "13688669343", "13688669344",
                "13688669345", "13688669346", "13688669347", "13688669348", "13688669349", "13688669350", "13688669351", "13688669352", "13688669353", "13688669354",
                "13688669355", "13688669356", "13688669357", "13688669358", "13688669359", "13688669360", "13688669361", "13688669362", "13688669363", "13688669364",
                "13688669365", "13688669366", "13688669367", "13688669368", "13688669369", "13688669370", "13688669371", "13688669372", "13688669373", "13688669374",
                "13688669375", "13688669376", "13688669377", "13688669378", "13688669379", "13688669380", "13688669381", "13688669382", "13688669383", "13688669384",
                "13688669385", "13688669386", "13688669387", "13688669388", "13688669389", "13688669390", "13688669391", "13688669392", "13688669393", "13688669394",
                "13688669395", "13688669396", "13688669397", "13688669398", "13688669399", "13688669400", "13688669401", "13688669402", "13688669403", "13688669404",
                "13688669405", "13688669406", "13688669407", "13688669408", "13688669409", "13688669410", "13688669411", "13688669412", "13688669413", "13688669414",
                "13688669415", "13688669416", "13688669417", "13688669418", "13688669419", "13688669420", "13688669421", "13688669422", "13688669423", "13688669424",
                "13688669425", "13688669426", "13688669427", "13688669428", "13688669429", "13688669430", "13688669431", "13688669432", "13688669433", "13688669434",
                "13688669435", "13688669436", "13688669437", "13688669438", "13688669439", "13688669440", "13688669441", "13688669442", "13688669443", "13688669444",
                "13688669445", "13688669446", "13688669447", "13688669448", "13688669449", "13688669450", "13688669451", "13688669452", "13688669453", "13688669454",
                "13688669455", "13688669456", "13688669457", "13688669458", "13688669459", "13688669460", "13688669461", "13688669462", "13688669463", "13688669464",
                "13688669465", "13688669466", "13688669467", "13688669468", "13688669469", "13688669470", "13688669471", "13688669472", "13688669473", "13688669474",
                "13688669475", "13688669476", "13688669477", "13688669478", "13688669479", "13688669480", "13688669481", "13688669482", "13688669483", "13688669484",
                "13688669485", "13688669486", "13688669487", "13688669488", "13688669489", "13688669490", "13688669491", "13688669492", "13688669493", "13688669494",
                "13688669495", "13688669496", "13688669497", "13688669498", "13688669499", "13688669500", "13688669501", "13688669502", "13688669503", "13688669504",
                "13688669505", "13688669506", "13688669507", "13688669508", "13688669509", "13688669510", "13688669511", "13688669512", "13688669513", "13688669514",
                "13688669515", "13688669516", "13688669517", "13688669518", "13688669519", "13688669520", "13688669521", "13688669522", "13688669523","13688669524",
                "13688669525", "13688669526", "13688669527", "13688669528", "13688669529", "13688669530", "13688669531", "13688669532", "13688669533", "13688669534",
                "13688669535", "13688669536", "13688669537", "13688669538", "13688669539", "13688669540", "13688669541", "13688669542", "13688669543", "13688669544",
                "13688669545", "13688669546", "13688669547", "13688669548", "13688669549", "13688669550", "13688669551", "13688669552", "13688669553", "13688669554",
                "13688669555", "13688669556", "13688669557", "13688669558", "13688669559", "13688669560", "13688669561", "13688669562", "13688669563", "13688669564",
                "13688669565", "13688669566", "13688669567", "13688669568", "13688669569", "13688669570", "13688669571", "13688669572", "13688669573", "13688669574",
                "13688669575", "13688669576", "13688669577", "13688669578", "13688669579", "13688669580", "13688669581", "13688669582", "13688669583", "13688669584",
                "13688669585", "13688669586", "13688669587", "13688669588", "13688669589", "13688669590", "13688669591", "13688669592", "13688669593", "13688669594",
                "13688669595", "13688669596", "13688669597", "13688669598", "13688669599", "13688669600", "13688669601", "13688669602", "13688669603", "13688669604",
                "13688669605", "13688669606", "13688669607", "13688669608", "13688669609", "13688669610", "13688669611", "13688669612", "13688669613", "13688669614",
                "13688669615", "13688669616", "13688669617", "13688669618", "13688669619", "13688669620", "13688669621", "13688669622", "13688669623", "13688669624",
                "13688669625", "13688669626", "13688669627", "13688669628", "13688669629", "13688669630", "13688669631", "13688669632", "13688669633", "13688669634",
                "13688669635", "13688669636", "13688669637", "13688669638", "13688669639", "13688669640", "13688669641", "13688669642", "13688669643", "13688669644",
                "13688669645", "13688669646", "13688669647", "13688669648", "13688669649", "13688669650", "13688669651", "13688669652", "13688669653", "13688669654",
                "13688669655", "13688669656", "13688669657", "13688669658", "13688669659", "13688669660", "13688669661", "13688669662", "13688669663", "13688669664",
                "13688669665", "13688669666", "13688669667", "13688669668", "13688669669", "13688669670", "13688669671", "13688669672", "13688669673", "13688669674",
                "13688669675", "13688669676", "13688669677", "13688669678", "13688669679", "13688669680", "13688669681", "13688669682", "13688669683", "13688669684",
                "13688669685", "13688669686", "13688669687", "13688669688", "13688669689", "13688669690", "13688669691", "13688669692", "13688669693", "13688669694",
                "13688669695", "13688669696", "13688669697", "13688669698", "13688669699", "13688669700", "13688669701", "13688669702", "13688669703", "13688669704",
                "13688669705", "13688669706", "13688669707", "13688669708", "13688669709", "13688669710", "13688669711", "13688669712", "13688669713", "13688669714",
                "13688669715", "13688669716", "13688669717", "13688669718", "13688669719", "13688669720", "13688669721", "13688669722", "13688669723", "13688669724",
                "13688669725", "13688669726", "13688669727", "13688669728", "13688669729", "13688669730", "13688669731", "13688669732", "13688669733", "13688669734",
                "13688669735", "13688669736", "13688669737", "13688669738", "13688669739", "13688669740", "13688669741", "13688669742", "13688669743", "13688669744",
                "13688669745", "13688669746", "13688669747", "13688669748", "13688669749", "13688669750", "13688669751", "13688669752", "13688669753", "13688669754",
                "13688669755", "13688669756", "13688669757", "13688669758", "13688669759", "13688669760", "13688669761", "13688669762", "13688669763", "13688669764",
                "13688669765", "13688669766", "13688669767", "13688669768", "13688669769", "13688669770", "13688669771", "13688669772", "13688669773", "13688669774",
                "13688669775", "13688669776", "13688669777", "13688669778", "13688669779", "13688669780", "13688669781", "13688669782", "13688669783", "13688669784",
                "13688669785", "13688669786", "13688669787", "13688669788", "13688669789", "13688669790", "13688669791", "13688669792", "13688669793", "13688669794",
                "13688669795", "13688669796", "13688669797", "13688669798", "13688669799", "13688669800", "13688669801", "13688669802", "13688669803", "13688669804",
                "13688669805", "13688669806", "13688669807", "13688669808", "13688669809", "13688669810", "13688669811", "13688669812", "13688669813", "13688669814",
                "13688669815", "13688669816", "13688669817", "13688669818", "13688669819", "13688669820", "13688669821", "13688669822", "13688669823", "13688669824",
                "13688669825", "13688669826", "13688669827", "13688669828", "13688669829", "13688669830", "13688669831", "13688669832", "13688669833", "13688669834",
                "13688669835", "13688669836", "13688669837", "13688669838", "13688669839", "13688669840", "13688669841", "13688669842", "13688669843", "13688669844",
                "13688669845", "13688669846", "13688669847", "13688669848","13688669849", "13688669849", "13688669850", "13688669851", "13688669852", "13688669853",
                "13688669854", "13688669855", "13688669856", "13688669857", "13688669858", "13688669859", "13688669860", "13688669861", "13688669862", "13688669863",
                "13688669864", "13688669865", "13688669866", "13688669867", "13688669868", "13688669869", "13688669870", "13688669871", "13688669872", "13688669873",
                "13688669874", "13688669875", "13688669876", "13688669877", "13688669878", "13688669879", "13688669880", "13688669881", "13688669882", "13688669883",
                "13688669884", "13688669885", "13688669886", "13688669887", "13688669888", "13838411438", "17359456898"
        };
        for(String phone : phoneNumbers){
            //一致根据手机号查用户
            User user = query().eq("phone", phone).one();
            //7.保存用户信息到redis----------------
            //7.1 随机生成Token作为登录令牌
            String token = UUID.randomUUID().toString(true);
            String filePath = "/Users/tongziyu/JavaCode/VueProjects/hmdp/output.txt";
            String content = token+'\n';
            try (FileWriter fileWriter = new FileWriter(filePath, true)) {
                try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                    // 写入内容
                    bufferedWriter.write(content);
                    // 确保内容都已写入文件
                    bufferedWriter.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println(token);
            //7.2 将User对象转为Hash存储
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
            //7.3 存储
            stringRedisTemplate.opsForHash().putAll("login:token:"+token,userMap);
            //7.4设置token有效期
            String tokenKey = LOGIN_USER_KEY+token;
            stringRedisTemplate.expire(tokenKey,999999999,TimeUnit.MINUTES);
        }
    }

}
