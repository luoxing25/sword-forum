package com.sword.control;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.sword.listen.Online;
import com.sword.mapper.*;
import com.sword.model.*;
import com.sword.model.VO.CommentVo;
import com.sword.model.VO.TopicCatalogVo;
import com.sword.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Decoder;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User 控制层
 */
@Controller
public class UserController {
    @Resource
    private UserMapper userMapper;
    @Resource
    private TopicMapper topicMapper;
    @Resource
    private ConcernMapper concernMapper;
    @Resource
    private LogtableMapper logtableMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private MessageControl messageControll;
    @Resource
    private FriendMapper friendMapper;
    @Resource
    private SixinMapper sixinMapper;
    @RequestMapping(value = "/checkLogin", method = RequestMethod.POST)
    public void checkLogin(@RequestParam("username") String username, @RequestParam("password") String password, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> mapwhere = new HashMap<String, Object>(2);
        mapwhere.put("uemail", username);
        mapwhere.put("upassword", password);
        List<User> userList = userMapper.selectByMap(mapwhere);
        if (userList != null && userList.size() != 0) {
            User user = userList.get(0);
            if (user.getUstate() == 0) {
                //记录登录表，每天的登录加一次积分
                IpUtil ipUtil = new IpUtil();
                String ip = ipUtil.getIp(request);
                Date date=new Date();
                Logtable logtable = new Logtable(user.getUid(),ip,date,1);
                logtableMapper.insert(logtable);
                //先插入了登录记录，所以为1才是第一次登录
                int count=logtableMapper.todayLoginCount(user.getUid(),date);
                if(count==1){
                    int oldpoint=user.getUpoint();
                    int newpoint=oldpoint+10;
                    int level=LevelUtil.point2Level(newpoint);
                    user.setUpoint(newpoint);
                    user.setUlevel(level);
                    userMapper.updateById(user);
                    request.getSession().setAttribute("loginfirst","yes");  //第一次登录
                    //System.out.println("==今天第一次登录===");
                }else {
                    request.getSession().setAttribute("loginfirst","no");
                   // System.out.println("==今天不是第一次登录====");
                }
                request.getSession().setAttribute("user", user);
                Online.add();
                //System.out.println("登录成功：" + user.getUnickname());
                try {
                    response.getWriter().write("success");
                    response.getWriter().flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                /////用户被封了
                try {
                    response.getWriter().write(""+user.getUstate());
                    response.getWriter().flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            try {
                response.getWriter().write("err");
                response.getWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @RequestMapping("/login")
    public String login(HttpServletRequest request) {
        User user = (User) request.getSession(false).getAttribute("user");
        if (user!=null&&user.getUid() != null) {
            return "redirect:/index.jsp";
        } else {
            return "redirect:/login.html";
        }
    }
    @RequestMapping("/firstLoginSession")
    public void changeLoginSession(HttpServletRequest request){
        HttpSession session=request.getSession(false);
        if(session!=null){
            String loginFirstFlag= (String) session.getAttribute("loginfirst");
            if(loginFirstFlag.equals("yes")){
                session.setAttribute("loginfirst","yesing");
            }
        }
    }
    @RequestMapping(value = "/checkRegister", method = RequestMethod.POST)
    public void checkRegister(@RequestParam("uemail") String uemail, @RequestParam("unickname") String unickname, @RequestParam("upassword") String upassword, PrintWriter pw) {
        /*******前后台双重验证，防止插入数据出错********/
        Pattern pattern1 = Pattern.compile("^(\\w-*\\.*)+@(\\w-?)+(\\.\\w{2,})+$");
        Matcher matcher1 = pattern1.matcher(uemail.trim());
        Pattern pattern2 = Pattern.compile("^[\\w]{6,12}$");
        Matcher matcher2 = pattern2.matcher(upassword.trim());
        if (matcher1.matches() && matcher2.matches() && unickname.trim().length() > 0) {
            Map<String, Object> mapwhere = new HashMap<String, Object>(1);
            mapwhere.put("uemail", uemail);
            List<User> user = userMapper.selectByMap(mapwhere);
            if (user == null || user.size() == 0) {
                User newUser = new User();
                newUser.setUemail(uemail);
                newUser.setUnickname(unickname);
                newUser.setUpassword(upassword);
                /*插入数据，同步防止数据库出现多条邮箱一样的*/
                int i = insertUser(newUser);
                if (i == 1) {
                    Friend sys=new Friend();
                    sys.setFromuid(-1L);
                    sys.setTouid(newUser.getUid());
                    sys.setTime(new Date());
                    friendMapper.insert(sys);
                    Sixin sixin=new Sixin();
                    sixin.setSifromuid(-1L);
                    sixin.setSitouid(newUser.getUid());
                    sixin.setContent("<p style='color:grern'>欢迎使用仗剑论坛，有你的世界更精彩!" +
                            "<br/>  <small> 站长：李胜助</small>" +
                            "</p>");
                    sixin.setIsread(0);
                    sixinMapper.insert(sixin);

                    pw.write("success");

                } else {
                    pw.write("busy");   //并发现象 繁忙
                }
            } else {
                pw.write("has");        //用户已被注册
            }
        } else {
            pw.write("illegal");
        }
    }

    @RequestMapping("/showmyplace")
    public String showmyplace(HttpServletRequest request, HttpServletResponse response) {
        User user = (User) request.getSession().getAttribute("user");
        long[] othernums = userOtherinfo(user.getUid());
        request.setAttribute("mytopicnum", othernums[0]);
        request.setAttribute("myconcernnum", othernums[1]);
        request.setAttribute("beconcernnum", othernums[2]);
        //查询我关注的人的最新动态
        List<Topic> topicList = concernMapper.concernTopic(user.getUid());
        List<TopicCatalogVo> topicCatalogVoList = null;
        if (topicList.size() != 0) {
            topicCatalogVoList = new ArrayList<TopicCatalogVo>(topicList.size());
            for (Topic t : topicList) {
                User concerner = userMapper.selectById(t.getTuid());
                TopicCatalogVo vo = toVoUtil.toTopciVO(t, concerner);
                topicCatalogVoList.add(vo);
            }
        }
        request.setAttribute("tcvos", topicCatalogVoList);
        return "myplace";
    }


    @RequestMapping("/showUser/{uid}")      //展示他人用户基本信息和帖子
    public String showUser(@PathVariable long uid, HttpServletRequest request) {
        User user = userMapper.selectById(uid);
        User me = (User) request.getSession().getAttribute("user");
        if (me == null) {
            return "redirect:/login.html";
        }
        if (uid == me.getUid()) {
            return "redirect:/showmyplace";
        }
        long[] othernums = userOtherinfo(uid);
        request.setAttribute("his", user);
        request.setAttribute("histopicnum", othernums[0]);
        request.setAttribute("hisconcernnum", othernums[1]);
        request.setAttribute("beconcernnum", othernums[2]);
        Concern concern = new Concern();
        concern.setConfromuid(me.getUid());
        concern.setContouid(uid);
        int i = concernMapper.selectCount(concern);
        if (i == 0) {
            request.setAttribute("flag", 0);
        } else {
            request.setAttribute("flag", 1);
        }
        EntityWrapper<Topic> ew = new EntityWrapper<Topic>();
        ew.where("tuid={0}", uid).orderBy("ttime", false);
        List<Topic> topicList = topicMapper.selectList(ew);
        List<TopicCatalogVo> topicCatalogVoList = null;
        if (topicList.size() != 0) {
            topicCatalogVoList = new ArrayList<TopicCatalogVo>(topicList.size());
            for (Topic t : topicList) {
                TopicCatalogVo vo = toVoUtil.toTopciVO(t, user);
                topicCatalogVoList.add(vo);
            }
        }
        request.setAttribute("tcvos", topicCatalogVoList);
        boolean isfriend=MessageControl.isFriend(uid,me.getUid(),friendMapper);
        if(isfriend){
            request.setAttribute("isfriend","yes");
        }else {
            request.setAttribute("isfriend","no");
        }
        return "hisplace";
    }

    private synchronized int insertUser(User user) {
        return userMapper.insertSelective(user);
    }

    /*我发布的帖子数目 我关注的人数目 关注我的人数目 除了自己别人也可以看到 */
    public long[] userOtherinfo(long uid) {
        long[] nums = new long[3];
        Topic topic = new Topic();
        topic.setTuid(uid);
        int topicnum = topicMapper.selectCount(topic);
        nums[0] = topicnum;
        /*我关注多少人*/
        Concern concern1 = new Concern();
        concern1.setConfromuid(uid);
        int myconcernnum = concernMapper.selectCount(concern1);
        nums[1] = myconcernnum;
        /*关注我多少人*/
        Concern concern2 = new Concern();
        concern2.setContouid(uid);
        int beconcernnum = concernMapper.selectCount(concern2);
        nums[2] = beconcernnum;
        return nums;
    }

    /*个人信息修改的功能*/
    @RequestMapping("/intoalterinfo")
    public String intoalterinfo(HttpServletRequest request) {

        return "alterinfo";
    }

    @RequestMapping(value = "/alterinfo", method = RequestMethod.POST)
    public void alterinfo(@RequestParam("nickname") String nickname,
                          @RequestParam(value = "sex") int sex,
                          @RequestParam(value = "birthday") String birthday,
                          @RequestParam(value = "headimg") String headimg,
                          @RequestParam("flag") int type,
                          @RequestParam(value = "statement") String statement,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        System.out.println(nickname + " " + sex + " " + birthday + "\n" + headimg + " \n" + type + " " + statement);
        User user = (User) request.getSession().getAttribute("user");
        user.setUnickname(nickname);
        user.setUsex(sex);
        user.setUbirthday(DateUtil.toDate_Formate(birthday));
        user.setUstatement(statement);
        int flag = 0;
        PrintWriter pw = null;
        if (type == 0) {    //不改头像
            flag = userMapper.updateById(user);
        } else if (type == 1) {  //改头像
            String dbpath = uploadheadimg(headimg, user, request);
            if (dbpath == null) {
                flag = 0;
            } else {
                user.setHeadimg(dbpath);
                flag = userMapper.updateById(user);
            }
        }
        if (flag == 1) {
            try {
                pw = response.getWriter();
                pw.write("成功");
                pw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                pw = response.getWriter();
                pw.write("失败");
                pw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public String uploadheadimg(String data, User user, HttpServletRequest request) {
        String imgdata = data.split(",")[1];
        BASE64Decoder base64Decoder = new BASE64Decoder();
        String path = user.getUid() + DateUtil.toDay_Format(new Date()) + ".png";
        String filepath = request.getServletContext().getRealPath("/") + "img" + File.separator + path;
        System.out.println(filepath);
        File file = new File(filepath);
        try {
            byte[] decoderbyte = base64Decoder.decodeBuffer(imgdata);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(decoderbyte);
            fos.close();
            return path;
        } catch (IOException e) {
            System.out.println("上传失败" + e.getMessage());
        }
        return null;
    }

    /*修改自己的密码*/
    @RequestMapping("/intoalterpassword")
    public String intoalterpassword() {
        return "alterpassword";
    }

    @RequestMapping("/sendyzm")
    public void sendyzm(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute("user");
        String uemail = user.getUemail();
        MailUtil mailUtil = new MailUtil();
        String yzm = RandomUtil.getyzm(4);
        String content = "您的验证码为：" + yzm + " --修改密码";
        mailUtil.sendSimpleMail(uemail, "仗剑论坛-Sword", content);
        //存放到application,当验证完清除
        request.getSession().getServletContext().setAttribute(uemail, yzm);
    }

    @RequestMapping("/alterpassword")
    public void alterpassword(HttpServletRequest request,
                              HttpServletResponse response,
                              @RequestParam("beginp") String beginp,
                              @RequestParam("endp") String endp,
                              @RequestParam("yzm") String yzm) throws Exception {
        User user = (User) request.getSession().getAttribute("user");
        String password = user.getUpassword();
        PrintWriter pw = null;
        if (!password.equals(beginp)) {
            pw = response.getWriter();
            pw.write("errpassword");
            pw.close();
            return;
        } else {
            String sendyzm = (String) request.getSession().getServletContext().getAttribute(user.getUemail());
            if (!sendyzm.equals(yzm)) {
                pw = response.getWriter();
                pw.write("erryzm");
                pw.close();
                return;
            } else {
                user.setUpassword(endp);
                userMapper.updateById(user);
                //记录
                String ip=new IpUtil().getIp(request);
                Logtable logtable=new Logtable(user.getUid(),ip,3);
                logtableMapper.insert(logtable);
                request.getSession().removeAttribute("user");
                pw = response.getWriter();
                pw.write("success");
                pw.close();
                request.getSession().getServletContext().removeAttribute(user.getUemail());
            }
        }
    }

    /**
     * 忘记密码
     **/
    @RequestMapping("/sendyzm2")
    public void sendyzm2(@RequestParam("eamil") String email,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        /**账号是否存在**/
        Map<String, Object> mapwhere = new HashMap<String, Object>(1);
        mapwhere.put("uemail", email);
        PrintWriter pw = null;

        if (userMapper.selectByMap(mapwhere).size() == 0) {
            pw = response.getWriter();
            pw.write("erremail");
            pw.close();
            return;
        } else {
            MailUtil mailUtil = new MailUtil();
            String yzm = RandomUtil.getyzm(6);
            mailUtil.sendSimpleMail(email, "仗剑论坛-Sword", "<h2>您的验证码为：</h2><font color='blue'>" + yzm + "</font>---重置您的密码");
            request.getSession().getServletContext().setAttribute(email, yzm);
        }
    }

    @RequestMapping("/forgetpassword")
    public void forgetpassword(@RequestParam("email") String email,
                               @RequestParam("yzm") String yzm,
                               @RequestParam("newpassword") String newpassword,
                               HttpServletRequest request,
                               HttpServletResponse response) throws Exception {
        String sendyzm = (String) request.getSession().getServletContext().getAttribute(email);
        if (sendyzm != null && sendyzm.length() > 0) {
            if (sendyzm.equals(yzm)) {
                Map<String, Object> mapwhere = new HashMap<String, Object>(1);
                mapwhere.put("ueamil", email);
                User user = userMapper.selectByMap(mapwhere).get(0);
                user.setUpassword(newpassword);
                int i = userMapper.updateById(user);
                if (i == 1) {
                    request.getSession().getServletContext().removeAttribute(email);
                    //记录
                    String ip=new IpUtil().getIp(request);
                    Logtable logtable=new Logtable(user.getUid(),ip,4);
                    logtableMapper.insert(logtable);
                    PrintWriter pw = response.getWriter();
                    pw.write("success");
                    pw.close();
                } else {
                    PrintWriter pw = response.getWriter();
                    pw.write("err");
                    pw.close();
                }
            } else {
                PrintWriter pw = response.getWriter();
                pw.write("erryzm");
                pw.close();
            }
        } else {
            PrintWriter pw = response.getWriter();
            pw.write("unknowerr");
            pw.close();
        }
    }

    /**
     * 管理我发布的帖子
     **/
    //先展示发布的帖子
    @RequestMapping("/mytopic")
    public String mytopic(HttpServletRequest request, Map<String, Object> map,
                          @RequestParam("page") int current, @RequestParam("condition") String condition) {
        Long start = System.currentTimeMillis();
        User me = (User) request.getSession().getAttribute("user");
        Page<Topic> page = new Page<Topic>(current, 8);
        EntityWrapper<Topic> ew = new EntityWrapper<Topic>();
        List<TopicCatalogVo> topicCatalogVos = null;
        if (condition == null || condition.equals("")) {
            ew.where("tuid={0}", me.getUid()).orderBy("ttime", false);
        } else {
            ew.where("tuid={0}", me.getUid()).like("ttopic", "'%" + condition + "%'").orderBy("ttime", false);
        }
        List<Topic> mytopics = topicMapper.selectPage(page, ew);
        if (mytopics.size() != 0) {
            topicCatalogVos = new ArrayList<TopicCatalogVo>(20);
            for (Topic t : mytopics) {
                TopicCatalogVo topicCatalogVo = toVoUtil.toTopciVO(t, me, 100, 1);
                topicCatalogVos.add(topicCatalogVo);
            }
        }
        Long end = System.currentTimeMillis();
        long needtime = end - start;
        map.put("tcvos", topicCatalogVos);
        map.put("pages", page.getPages());
        map.put("condition", condition);
        map.put("needtime", needtime);
        return "mytopic";
    }

    //删除帖子
    @RequestMapping("/deleteMytopic")
    public void deleteMyTopic(@RequestParam("tid") long tid, HttpServletResponse response,HttpServletRequest request) throws Exception {
        int i = topicMapper.deleteById(tid);
        try {
            PrintWriter pw = response.getWriter();
            if (i == 1) {
                //记录
                String ip=new IpUtil().getIp(request);
                User user= (User) request.getSession(false).getAttribute("user");
                Logtable logtable=new Logtable(user.getUid(),ip,6);
                logtableMapper.insert(logtable);
                pw.write("ok");
            } else {
                pw.write("err");
            }
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * --EDD管理发布的帖子
     **/

    @RequestMapping("/toiconcern")     //跳转到我关注的人 的jsp页面
    public String toIConcern() {
        return "iConcern";
    }

    @RequestMapping("/toconcerni")
    public String toConcernI() {
        return "concernI";
    }

    @RequestMapping("/leave")
    public String leave(HttpServletRequest request) throws Exception {
        HttpSession session=request.getSession(false);
        if(session!=null){
            User user= (User) session.getAttribute("user");
            Logtable logtable=new Logtable(user.getUid(),new IpUtil().getIp(request),2);
            logtableMapper.insert(logtable);
        }
        session.invalidate();
        return "redirect:login.html";
    }
    /**我的评论 **/
    @RequestMapping("/tomycomment")
    public String toManComments(){
        return "mycomment";
    }
    @RequestMapping("/listmycomment")
    @ResponseBody
    public List<CommentVo> listMyComments(HttpServletRequest request){
        User me= (User) request.getSession(false).getAttribute("user");
        Map mapwhere=new HashMap(1);
        mapwhere.put("cuid",me.getUid());
        List<Comment> comments=commentMapper.selectByMap(mapwhere);
        List<CommentVo> commentVos=new ArrayList<>(comments.size());
        System.out.println(comments.size());
        for (Comment comment:comments) {
            CommentVo commentVo=CommentController.comment2Vo(comment,userMapper,topicMapper);
            commentVos.add(commentVo);
        }
        return commentVos;
    }
    @RequestMapping("/deletemycomment")
    public  void deleteComment(@RequestParam("cid")long cid,HttpServletResponse response,HttpServletRequest request) throws Exception {
        int i=commentMapper.deleteById(cid);
        PrintWriter pw=response.getWriter();
        User user= (User) request.getSession(false).getAttribute("user");
        if(i==1){
            //记录
            Logtable logtable=new Logtable(user.getUid(),new IpUtil().getIp(request),8);
            logtableMapper.insert(logtable);
            pw.write("success");
        }else {
            pw.write("err");
        }
        pw.close();
    }
    @RequestMapping("/deletemycommentbatch")
    public void deleteCommentBatch(@RequestParam("cids")long[] cids,HttpServletResponse response,HttpServletRequest request) throws Exception {
        List cidlist=Arrays.asList(cids);
        int i=commentMapper.deleteBatchIds(cidlist);
        PrintWriter pw=response.getWriter();
        User user= (User) request.getSession(false).getAttribute("user");
        if (i != cidlist.size()) {
            //记录
            Logtable logtable=new Logtable(user.getUid(),new IpUtil().getIp(request),8);
            logtableMapper.insert(logtable);
            pw.write("err");}
        else {
            pw.write("success");
        }
        pw.close();
    }



}
