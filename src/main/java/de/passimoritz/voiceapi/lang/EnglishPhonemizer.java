package de.passimoritz.voiceapi.lang;

import de.passimoritz.voiceapi.Compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.passimoritz.voiceapi.lang.Phoneme.*;

/**
 * English: a dictionary of frequent and irregular words (English spelling often does not reveal the pronunciation) and rules for
 * everything else: endings (-ing, -ed, -s, -tion, -ture, -le …), silent final e with a long vowel (name, time, hope), vowel digraphs (ee, ea,
 * oo, ou, ow, ai, oa, igh …), c/g before e/i/y, s between vowels, open and closed syllables. Stress from the dictionary or: first syllable;
 * after unstressed prefixes (a-, be-, re-, de-, pre-, com-, con-, ex-, un-, dis-) the second; -tion/-sion/-ic/-ity stress the syllable before.
 */
public final class EnglishPhonemizer implements Phonemizer {

    public static final EnglishPhonemizer INSTANCE = new EnglishPhonemizer();

    private static final Set<String> ONSETS = Set.of(
            "pl", "pr", "bl", "br", "tr", "dr", "kl", "kr", "kw", "gl", "gr", "fl", "fr", "θr", "ʃr", "sl", "sm", "sn", "sp", "st", "sk", "sw",
            "spr", "str", "skr", "spl", "skw", "tw", "dw", "θw", "pj", "bj", "kj", "fj", "vj", "mj", "nj", "hj", "lj");

    private static final Map<String, List<Syllable>> LEXICON = new HashMap<>();

    static {
        String entries = """
                the=ðə a=ə an=ən and=ənd of=əv to=tə in=ˈɪn is=ˈɪz it=ˈɪt you=ˈjuː that=ˈðæt he=ˈhiː she=ˈʃiː we=ˈwiː they=ˈðeɪ was=ˈwʌz
                i=ˈaɪ emerald=ˈɛ.mə.rəld emeralds=ˈɛ.mə.rəldz traveler=ˈtræ.və.lɐ travelers=ˈtræ.və.lɐz stranger=ˈstreɪn.dʒɐ iron=ˈaɪ.ɐn
                for=ˈfɔːr on=ˈɔn are=ˈaːr as=ˈæz with=ˈwɪð his=ˈhɪz her=ˈhɜːr be=ˈbiː at=ˈæt one=ˈwʌn have=ˈhæv has=ˈhæz had=ˈhæd this=ˈðɪs
                from=ˈfrʌm or=ˈɔːr by=ˈbaɪ but=ˈbʌt not=ˈnɔt what=ˈwʌt all=ˈɔːl were=ˈwɜːr when=ˈwɛn your=ˈjɔːr can=ˈkæn said=ˈsɛd there=ˈðɛr
                their=ˈðɛr use=ˈjuːz each=ˈiːtʃ which=ˈwɪtʃ do=ˈduː does=ˈdʌz did=ˈdɪd done=ˈdʌn how=ˈhaʊ if=ˈɪf will=ˈwɪl up=ˈʌp other=ˈʌ.ðɚ
                about=ə.ˈbaʊt out=ˈaʊt many=ˈmɛ.ni then=ˈðɛn them=ˈðɛm these=ˈðiːz so=ˈsoʊ some=ˈsʌm would=ˈwʊd could=ˈkʊd should=ˈʃʊd
                into=ˈɪn.tuː time=ˈtaɪm two=ˈtuː more=ˈmɔːr go=ˈgoʊ no=ˈnoʊ way=ˈweɪ my=ˈmaɪ than=ˈðæn first=ˈfɜːrst water=ˈwɔː.tɚ been=ˈbɪn
                who=ˈhuː oil=ˈɔɪl now=ˈnaʊ find=ˈfaɪnd long=ˈlɔŋ down=ˈdaʊn day=ˈdeɪ get=ˈgɛt come=ˈkʌm made=ˈmeɪd may=ˈmeɪ part=ˈpaːrt
                over=ˈoʊ.vɚ new=ˈnuː sound=ˈsaʊnd take=ˈteɪk only=ˈoʊn.li little=ˈlɪ.təl work=ˈwɜːrk know=ˈnoʊ place=ˈpleɪs year=ˈjɪr
                live=ˈlɪv me=ˈmiː back=ˈbæk give=ˈgɪv most=ˈmoʊst very=ˈvɛ.ri after=ˈæf.tɚ thing=ˈθɪŋ our=ˈaʊr just=ˈdʒʌst name=ˈneɪm
                good=ˈgʊd sentence=ˈsɛn.təns man=ˈmæn think=ˈθɪŋk say=ˈseɪ great=ˈgreɪt where=ˈwɛr help=ˈhɛlp through=ˈθruː much=ˈmʌtʃ
                before=bɪ.ˈfɔːr line=ˈlaɪn right=ˈraɪt too=ˈtuː mean=ˈmiːn old=ˈoʊld any=ˈɛ.ni same=ˈseɪm tell=ˈtɛl boy=ˈbɔɪ follow=ˈfɔ.loʊ
                came=ˈkeɪm want=ˈwɔnt show=ˈʃoʊ also=ˈɔːl.soʊ around=ə.ˈraʊnd form=ˈfɔːrm three=ˈθriː small=ˈsmɔːl set=ˈsɛt put=ˈpʊt
                end=ˈɛnd why=ˈwaɪ again=ə.ˈgɛn turn=ˈtɜːrn here=ˈhɪr off=ˈɔf went=ˈwɛnt number=ˈnʌm.bɚ great=ˈgreɪt men=ˈmɛn
                every=ˈɛv.ri near=ˈnɪr add=ˈæd food=ˈfuːd between=bɪ.ˈtwiːn own=ˈoʊn below=bɪ.ˈloʊ country=ˈkʌn.tri plant=ˈplænt
                last=ˈlæst school=ˈskuːl father=ˈfaː.ðɚ keep=ˈkiːp tree=ˈtriː never=ˈnɛ.vɚ start=ˈstaːrt city=ˈsɪ.ti earth=ˈɜːrθ eye=ˈaɪ
                light=ˈlaɪt thought=ˈθɔːt head=ˈhɛd under=ˈʌn.dɚ story=ˈstɔː.ri saw=ˈsɔː left=ˈlɛft don't=ˈdoʊnt few=ˈfjuː while=ˈwaɪl
                along=ə.ˈlɔŋ might=ˈmaɪt close=ˈkloʊs something=ˈsʌm.θɪŋ seem=ˈsiːm next=ˈnɛkst hard=ˈhaːrd open=ˈoʊ.pən example=ɪg.ˈzæm.pəl
                begin=bɪ.ˈgɪn life=ˈlaɪf always=ˈɔːl.weɪz those=ˈðoʊz both=ˈboʊθ paper=ˈpeɪ.pɚ together=tə.ˈgɛ.ðɚ got=ˈgɔt group=ˈgruːp
                often=ˈɔ.fən run=ˈrʌn important=ɪm.ˈpɔːr.tənt until=ʌn.ˈtɪl children=ˈtʃɪl.drən side=ˈsaɪd feet=ˈfiːt car=ˈkaːr mile=ˈmaɪl
                night=ˈnaɪt walk=ˈwɔːk white=ˈwaɪt sea=ˈsiː began=bɪ.ˈgæn grow=ˈgroʊ took=ˈtʊk river=ˈrɪ.vɚ four=ˈfɔːr carry=ˈkæ.ri
                state=ˈsteɪt once=ˈwʌns book=ˈbʊk hear=ˈhɪr stop=ˈstɔp without=wɪ.ˈðaʊt second=ˈsɛ.kənd later=ˈleɪ.tɚ miss=ˈmɪs idea=aɪ.ˈdiː.ə
                enough=ɪ.ˈnʌf eat=ˈiːt face=ˈfeɪs watch=ˈwɔtʃ far=ˈfaːr real=ˈriːl almost=ˈɔːl.moʊst let=ˈlɛt above=ə.ˈbʌv girl=ˈgɜːrl
                sometimes=ˈsʌm.taɪmz mountain=ˈmaʊn.tən cut=ˈkʌt young=ˈjʌŋ talk=ˈtɔːk soon=ˈsuːn list=ˈlɪst song=ˈsɔŋ being=ˈbiː.ɪŋ leave=ˈliːv
                family=ˈfæ.mə.li body=ˈbɔ.di music=ˈmjuː.zɪk color=ˈkʌ.lɚ stand=ˈstænd sun=ˈsʌn questions=ˈkwɛs.tʃənz question=ˈkwɛs.tʃən
                fish=ˈfɪʃ area=ˈɛ.ri.ə mark=ˈmaːrk dog=ˈdɔg horse=ˈhɔːrs birds=ˈbɜːrdz problem=ˈprɔ.bləm complete=kəm.ˈpliːt room=ˈruːm
                knew=ˈnuː since=ˈsɪns ever=ˈɛ.vɚ piece=ˈpiːs told=ˈtoʊld usually=ˈjuː.ʒu.ə.li didn't=ˈdɪ.dənt friends=ˈfrɛndz friend=ˈfrɛnd
                easy=ˈiː.zi heard=ˈhɜːrd order=ˈɔːr.dɚ red=ˈrɛd door=ˈdɔːr sure=ˈʃʊr become=bɪ.ˈkʌm top=ˈtɔp ship=ˈʃɪp across=ə.ˈkrɔs today=tə.ˈdeɪ
                during=ˈdʊ.rɪŋ short=ˈʃɔːrt better=ˈbɛ.tɚ best=ˈbɛst however=haʊ.ˈɛ.vɚ low=ˈloʊ hours=ˈaʊ.ɚz hour=ˈaʊr black=ˈblæk
                products=ˈprɔ.dʌkts happened=ˈhæ.pənd whole=ˈhoʊl measure=ˈmɛ.ʒɚ remember=rɪ.ˈmɛm.bɚ early=ˈɜːr.li waves=ˈweɪvz
                reached=ˈriːtʃt listen=ˈlɪ.sən wind=ˈwɪnd rock=ˈrɔk space=ˈspeɪs covered=ˈkʌ.vɚd fast=ˈfæst several=ˈsɛv.rəl hold=ˈhoʊld
                himself=hɪm.ˈsɛlf toward=tə.ˈwɔːrd five=ˈfaɪv step=ˈstɛp morning=ˈmɔːr.nɪŋ passed=ˈpæst vowel=ˈvaʊ.əl true=ˈtruː hundred=ˈhʌn.drəd
                against=ə.ˈgɛnst pattern=ˈpæ.tɚn numeral=ˈnuː.mə.rəl table=ˈteɪ.bəl north=ˈnɔːrθ slowly=ˈsloʊ.li money=ˈmʌ.ni map=ˈmæp
                farm=ˈfaːrm pulled=ˈpʊld draw=ˈdrɔː voice=ˈvɔɪs seen=ˈsiːn cold=ˈkoʊld cried=ˈkraɪd plan=ˈplæn notice=ˈnoʊ.tɪs south=ˈsaʊθ
                sing=ˈsɪŋ war=ˈwɔːr ground=ˈgraʊnd fall=ˈfɔːl king=ˈkɪŋ town=ˈtaʊn unit=ˈjuː.nɪt figure=ˈfɪ.gjɚ certain=ˈsɜːr.tən field=ˈfiːld
                travel=ˈtræ.vəl wood=ˈwʊd fire=ˈfaɪr upon=ə.ˈpɔn done=ˈdʌn english=ˈɪŋ.glɪʃ road=ˈroʊd half=ˈhæf ten=ˈtɛn fly=ˈflaɪ gave=ˈgeɪv
                box=ˈbɔks finally=ˈfaɪ.nə.li wait=ˈweɪt correct=kə.ˈrɛkt oh=ˈoʊ quickly=ˈkwɪk.li person=ˈpɜːr.sən became=bɪ.ˈkeɪm shown=ˈʃoʊn
                minutes=ˈmɪ.nɪts minute=ˈmɪ.nɪt strong=ˈstrɔŋ verb=ˈvɜːrb stars=ˈstaːrz front=ˈfrʌnt feel=ˈfiːl fact=ˈfækt inches=ˈɪn.tʃɪz street=ˈstriːt
                decided=dɪ.ˈsaɪ.dɪd contain=kən.ˈteɪn course=ˈkɔːrs surface=ˈsɜːr.fɪs produce=prə.ˈduːs building=ˈbɪl.dɪŋ ocean=ˈoʊ.ʃən class=ˈklæs
                note=ˈnoʊt nothing=ˈnʌ.θɪŋ rest=ˈrɛst carefully=ˈkɛr.fə.li scientists=ˈsaɪ.ən.tɪsts inside=ɪn.ˈsaɪd wheels=ˈwiːlz stay=ˈsteɪ
                green=ˈgriːn known=ˈnoʊn island=ˈaɪ.lənd week=ˈwiːk less=ˈlɛs machine=mə.ˈʃiːn base=ˈbeɪs ago=ə.ˈgoʊ stood=ˈstʊd plane=ˈpleɪn
                system=ˈsɪs.təm behind=bɪ.ˈhaɪnd ran=ˈræn round=ˈraʊnd boat=ˈboʊt game=ˈgeɪm force=ˈfɔːrs brought=ˈbrɔːt understand=ʌn.dɚ.ˈstænd
                warm=ˈwɔːrm common=ˈkɔ.mən bring=ˈbrɪŋ explain=ɪk.ˈspleɪn dry=ˈdraɪ though=ˈðoʊ language=ˈlæŋ.gwɪdʒ shape=ˈʃeɪp deep=ˈdiːp
                thousands=ˈθaʊ.zəndz thousand=ˈθaʊ.zənd yes=ˈjɛs clear=ˈklɪr equation=ɪ.ˈkweɪ.ʒən yet=ˈjɛt government=ˈgʌ.vɚn.mənt filled=ˈfɪld
                heat=ˈhiːt full=ˈfʊl hot=ˈhɔt check=ˈtʃɛk object=ˈɔb.dʒɛkt am=ˈæm rule=ˈruːl among=ə.ˈmʌŋ noun=ˈnaʊn power=ˈpaʊ.ɚ cannot=ˈkæ.nɔt
                able=ˈeɪ.bəl six=ˈsɪks size=ˈsaɪz dark=ˈdaːrk ball=ˈbɔːl material=mə.ˈtɪ.ri.əl special=ˈspɛ.ʃəl heavy=ˈhɛ.vi fine=ˈfaɪn pair=ˈpɛr
                circle=ˈsɜːr.kəl include=ɪn.ˈkluːd built=ˈbɪlt can't=ˈkænt matter=ˈmæ.tɚ square=ˈskwɛr syllables=ˈsɪ.lə.bəlz perhaps=pɚ.ˈhæps
                bill=ˈbɪl felt=ˈfɛlt suddenly=ˈsʌ.dən.li test=ˈtɛst direction=dɪ.ˈrɛk.ʃən center=ˈsɛn.tɚ farmers=ˈfaːr.mɚz ready=ˈrɛ.di anything=ˈɛ.ni.θɪŋ
                divided=dɪ.ˈvaɪ.dɪd general=ˈdʒɛ.nə.rəl energy=ˈɛ.nɚ.dʒi subject=ˈsʌb.dʒɪkt europe=ˈjʊ.rəp moon=ˈmuːn region=ˈriː.dʒən return=rɪ.ˈtɜːrn
                believe=bɪ.ˈliːv dance=ˈdæns members=ˈmɛm.bɚz picked=ˈpɪkt simple=ˈsɪm.pəl cells=ˈsɛlz paint=ˈpeɪnt mind=ˈmaɪnd love=ˈlʌv
                cause=ˈkɔːz rain=ˈreɪn exercise=ˈɛk.sɚ.saɪz eggs=ˈɛgz train=ˈtreɪn blue=ˈbluː wish=ˈwɪʃ drop=ˈdrɔp developed=dɪ.ˈvɛ.ləpt window=ˈwɪn.doʊ
                difference=ˈdɪ.frəns distance=ˈdɪs.təns heart=ˈhaːrt sit=ˈsɪt sum=ˈsʌm summer=ˈsʌ.mɚ wall=ˈwɔːl forest=ˈfɔ.rɪst probably=ˈprɔ.bə.bli
                legs=ˈlɛgz sat=ˈsæt main=ˈmeɪn winter=ˈwɪn.tɚ wide=ˈwaɪd written=ˈrɪ.tən length=ˈlɛŋkθ reason=ˈriː.zən kept=ˈkɛpt interest=ˈɪn.trəst
                arms=ˈaːrmz brother=ˈbrʌ.ðɚ race=ˈreɪs present=ˈprɛ.zənt beautiful=ˈbjuː.tɪ.fəl store=ˈstɔːr job=ˈdʒɔb edge=ˈɛdʒ past=ˈpæst sign=ˈsaɪn
                record=ˈrɛ.kɚd finished=ˈfɪ.nɪʃt discovered=dɪ.ˈskʌ.vɚd wild=ˈwaɪld happy=ˈhæ.pi beside=bɪ.ˈsaɪd gone=ˈgɔn sky=ˈskaɪ glass=ˈglæs
                million=ˈmɪl.jən west=ˈwɛst lay=ˈleɪ weather=ˈwɛ.ðɚ root=ˈruːt instruments=ˈɪn.strə.mənts meet=ˈmiːt third=ˈθɜːrd months=ˈmʌnθs
                paragraph=ˈpæ.rə.græf raised=ˈreɪzd represent=rɛ.prɪ.ˈzɛnt soft=ˈsɔft whether=ˈwɛ.ðɚ clothes=ˈkloʊðz flowers=ˈflaʊ.ɚz shall=ˈʃæl
                teacher=ˈtiː.tʃɚ held=ˈhɛld describe=dɪ.ˈskraɪb drive=ˈdraɪv cross=ˈkrɔs speak=ˈspiːk solve=ˈsɔlv appear=ə.ˈpɪr metal=ˈmɛ.təl son=ˈsʌn
                either=ˈiː.ðɚ ice=ˈaɪs sleep=ˈsliːp village=ˈvɪ.lɪdʒ factors=ˈfæk.tɚz result=rɪ.ˈzʌlt jumped=ˈdʒʌmpt snow=ˈsnoʊ ride=ˈraɪd care=ˈkɛr
                floor=ˈflɔːr hill=ˈhɪl pushed=ˈpʊʃt baby=ˈbeɪ.bi buy=ˈbaɪ century=ˈsɛn.tʃə.ri outside=aʊt.ˈsaɪd everything=ˈɛv.ri.θɪŋ tall=ˈtɔːl
                already=ɔːl.ˈrɛ.di instead=ɪn.ˈstɛd phrase=ˈfreɪz soil=ˈsɔɪl bed=ˈbɛd copy=ˈkɔ.pi free=ˈfriː hope=ˈhoʊp spring=ˈsprɪŋ case=ˈkeɪs
                laughed=ˈlæft nation=ˈneɪ.ʃən quite=ˈkwaɪt type=ˈtaɪp themselves=ðəm.ˈsɛlvz temperature=ˈtɛm.prə.tʃɚ bright=ˈbraɪt lead=ˈliːd
                everyone=ˈɛv.ri.wʌn method=ˈmɛ.θəd section=ˈsɛk.ʃən lake=ˈleɪk iron=ˈaɪ.ɚn within=wɪ.ˈðɪn dictionary=ˈdɪk.ʃə.nɛ.ri hair=ˈhɛr age=ˈeɪdʒ
                amount=ə.ˈmaʊnt scale=ˈskeɪl pounds=ˈpaʊndz although=ɔːl.ˈðoʊ per=ˈpɚ broken=ˈbroʊ.kən moment=ˈmoʊ.mənt tiny=ˈtaɪ.ni possible=ˈpɔ.sə.bəl
                gold=ˈgoʊld milk=ˈmɪlk quiet=ˈkwaɪ.ət natural=ˈnæ.tʃə.rəl lot=ˈlɔt stone=ˈstoʊn act=ˈækt build=ˈbɪld middle=ˈmɪ.dəl speed=ˈspiːd
                count=ˈkaʊnt cat=ˈkæt someone=ˈsʌm.wʌn sail=ˈseɪl rolled=ˈroʊld bear=ˈbɛr wonder=ˈwʌn.dɚ smiled=ˈsmaɪld angle=ˈæŋ.gəl fraction=ˈfræk.ʃən
                africa=ˈæ.frɪ.kə killed=ˈkɪld melody=ˈmɛ.lə.di bottom=ˈbɔ.təm trip=ˈtrɪp hole=ˈhoʊl poor=ˈpʊr let's=ˈlɛts fight=ˈfaɪt surprise=sɚ.ˈpraɪz
                french=ˈfrɛntʃ died=ˈdaɪd beat=ˈbiːt exactly=ɪg.ˈzækt.li remain=rɪ.ˈmeɪn dress=ˈdrɛs cat=ˈkæt couldn't=ˈkʊ.dənt fingers=ˈfɪŋ.gɚz
                row=ˈroʊ least=ˈliːst catch=ˈkætʃ climbed=ˈklaɪmd wrote=ˈroʊt shouted=ˈʃaʊ.tɪd continued=kən.ˈtɪn.juːd itself=ɪt.ˈsɛlf else=ˈɛls
                plains=ˈpleɪnz gas=ˈgæs england=ˈɪŋ.glənd burning=ˈbɜːr.nɪŋ design=dɪ.ˈzaɪn joined=ˈdʒɔɪnd foot=ˈfʊt law=ˈlɔː ears=ˈɪrz grass=ˈgræs
                you're=ˈjʊr grew=ˈgruː skin=ˈskɪn valley=ˈvæ.li cents=ˈsɛnts key=ˈkiː president=ˈprɛ.zɪ.dənt brown=ˈbraʊn trouble=ˈtrʌ.bəl cool=ˈkuːl
                cloud=ˈklaʊd lost=ˈlɔst sent=ˈsɛnt symbols=ˈsɪm.bəlz wear=ˈwɛr bad=ˈbæd save=ˈseɪv experiment=ɪk.ˈspɛ.rɪ.mənt engine=ˈɛn.dʒɪn alone=ə.ˈloʊn
                drawing=ˈdrɔː.ɪŋ east=ˈiːst pay=ˈpeɪ single=ˈsɪŋ.gəl touch=ˈtʌtʃ information=ɪn.fɚ.ˈmeɪ.ʃən express=ɪk.ˈsprɛs mouth=ˈmaʊθ yard=ˈjaːrd
                equal=ˈiː.kwəl decimal=ˈdɛ.sɪ.məl yourself=jɔːr.ˈsɛlf control=kən.ˈtroʊl practice=ˈpræk.tɪs report=rɪ.ˈpɔːrt straight=ˈstreɪt rise=ˈraɪz
                statement=ˈsteɪt.mənt stick=ˈstɪk party=ˈpaːr.ti seeds=ˈsiːdz suppose=sə.ˈpoʊz woman=ˈwʊ.mən women=ˈwɪ.mɪn coast=ˈkoʊst bank=ˈbæŋk
                period=ˈpɪ.ri.əd wire=ˈwaɪr choose=ˈtʃuːz clean=ˈkliːn visit=ˈvɪ.zɪt bit=ˈbɪt whose=ˈhuːz received=rɪ.ˈsiːvd garden=ˈgaːr.dən
                please=ˈpliːz strange=ˈstreɪndʒ caught=ˈkɔːt fell=ˈfɛl team=ˈtiːm god=ˈgɔd captain=ˈkæp.tən direct=dɪ.ˈrɛkt ring=ˈrɪŋ serve=ˈsɜːrv
                child=ˈtʃaɪld desert=ˈdɛ.zɚt increase=ɪn.ˈkriːs history=ˈhɪs.tə.ri cost=ˈkɔst maybe=ˈmeɪ.bi business=ˈbɪz.nɪs separate=ˈsɛ.pə.rət
                break=ˈbreɪk uncle=ˈʌŋ.kəl hunting=ˈhʌn.tɪŋ flow=ˈfloʊ lady=ˈleɪ.di students=ˈstuː.dənts human=ˈhjuː.mən art=ˈaːrt feeling=ˈfiː.lɪŋ
                supply=sə.ˈplaɪ corner=ˈkɔːr.nɚ electric=ɪ.ˈlɛk.trɪk insects=ˈɪn.sɛkts crops=ˈkrɔps tone=ˈtoʊn hit=ˈhɪt sand=ˈsænd doctor=ˈdɔk.tɚ
                provide=prə.ˈvaɪd thus=ˈðʌs won't=ˈwoʊnt cook=ˈkʊk bones=ˈboʊnz tail=ˈteɪl board=ˈbɔːrd modern=ˈmɔ.dɚn compound=ˈkɔm.paʊnd mine=ˈmaɪn
                wasn't=ˈwʌ.zənt fit=ˈfɪt addition=ə.ˈdɪ.ʃən belong=bɪ.ˈlɔŋ safe=ˈseɪf soldiers=ˈsoʊl.dʒɚz guess=ˈgɛs silent=ˈsaɪ.lənt trade=ˈtreɪd
                rather=ˈræ.ðɚ compare=kəm.ˈpɛr crowd=ˈkraʊd poem=ˈpoʊ.əm enjoy=ɪn.ˈdʒɔɪ elements=ˈɛ.lə.mənts indicate=ˈɪn.dɪ.keɪt except=ɪk.ˈsɛpt
                expect=ɪk.ˈspɛkt flat=ˈflæt seven=ˈsɛ.vən interesting=ˈɪn.trə.stɪŋ sense=ˈsɛns string=ˈstrɪŋ blow=ˈbloʊ famous=ˈfeɪ.məs value=ˈvæl.juː
                wings=ˈwɪŋz movement=ˈmuːv.mənt pole=ˈpoʊl exciting=ɪk.ˈsaɪ.tɪŋ branches=ˈbræn.tʃɪz thick=ˈθɪk blood=ˈblʌd lie=ˈlaɪ spot=ˈspɔt bell=ˈbɛl
                fun=ˈfʌn loud=ˈlaʊd consider=kən.ˈsɪ.dɚ suggested=sə.ˈdʒɛs.tɪd thin=ˈθɪn position=pə.ˈzɪ.ʃən entered=ˈɛn.tɚd fruit=ˈfruːt tied=ˈtaɪd
                rich=ˈrɪtʃ dollars=ˈdɔ.lɚz send=ˈsɛnd sight=ˈsaɪt chief=ˈtʃiːf japanese=dʒæ.pə.ˈniːz stream=ˈstriːm planets=ˈplæ.nɪts rhythm=ˈrɪ.ðəm
                eight=ˈeɪt science=ˈsaɪ.əns major=ˈmeɪ.dʒɚ observe=əb.ˈzɜːrv tube=ˈtuːb necessary=ˈnɛ.sə.sɛ.ri weight=ˈweɪt meat=ˈmiːt lifted=ˈlɪf.tɪd
                process=ˈprɔ.sɛs army=ˈaːr.mi hat=ˈhæt property=ˈprɔ.pɚ.ti particular=pɚ.ˈtɪk.jə.lɚ swim=ˈswɪm terms=ˈtɜːrmz current=ˈkʌ.rənt park=ˈpaːrk
                sell=ˈsɛl shoulder=ˈʃoʊl.dɚ industry=ˈɪn.də.stri wash=ˈwɔʃ block=ˈblɔk spread=ˈsprɛd cattle=ˈkæ.təl wife=ˈwaɪf sharp=ˈʃaːrp company=ˈkʌm.pə.ni
                radio=ˈreɪ.di.oʊ we'll=ˈwiːl action=ˈæk.ʃən capital=ˈkæ.pɪ.təl factories=ˈfæk.tə.riz settled=ˈsɛ.təld yellow=ˈjɛ.loʊ isn't=ˈɪ.zənt
                southern=ˈsʌ.ðɚn truck=ˈtrʌk fair=ˈfɛr printed=ˈprɪn.tɪd wouldn't=ˈwʊ.dənt ahead=ə.ˈhɛd chance=ˈtʃæns born=ˈbɔːrn level=ˈlɛ.vəl
                triangle=ˈtraɪ.æŋ.gəl molecules=ˈmɔ.lə.kjuːlz france=ˈfræns repeated=rɪ.ˈpiː.tɪd column=ˈkɔ.ləm western=ˈwɛs.tɚn church=ˈtʃɜːrtʃ
                sister=ˈsɪs.tɚ oxygen=ˈɔk.sɪ.dʒən plural=ˈplʊ.rəl various=ˈvɛ.ri.əs agreed=ə.ˈgriːd opposite=ˈɔ.pə.zɪt wrong=ˈrɔŋ chart=ˈtʃaːrt
                prepared=prɪ.ˈpɛrd pretty=ˈprɪ.ti solution=sə.ˈluː.ʃən fresh=ˈfrɛʃ shop=ˈʃɔp suffix=ˈsʌ.fɪks especially=ɪ.ˈspɛ.ʃə.li shoes=ˈʃuːz
                actually=ˈæk.tʃu.ə.li nose=ˈnoʊz afraid=ə.ˈfreɪd dead=ˈdɛd sugar=ˈʃʊ.gɚ adjective=ˈæ.dʒɪk.tɪv fig=ˈfɪg office=ˈɔ.fɪs huge=ˈhjuːdʒ
                army=ˈaːr.mi hello=hɛ.ˈloʊ hi=ˈhaɪ hey=ˈheɪ bye=ˈbaɪ goodbye=gʊd.ˈbaɪ thanks=ˈθæŋks thank=ˈθæŋk welcome=ˈwɛl.kəm sorry=ˈsɔ.ri
                okay=oʊ.ˈkeɪ ok=oʊ.ˈkeɪ player=ˈpleɪ.ɚ players=ˈpleɪ.ɚz game=ˈgeɪm games=ˈgeɪmz minecraft=ˈmaɪn.kræft creeper=ˈkriː.pɚ
                villager=ˈvɪ.lɪ.dʒɚ villagers=ˈvɪ.lɪ.dʒɚz zombie=ˈzɔm.bi diamond=ˈdaɪ.mənd diamonds=ˈdaɪ.məndz gonna=ˈgɔ.nə wanna=ˈwɔ.nə
                i'm=ˈaɪm i'll=ˈaɪl i've=ˈaɪv it's=ˈɪts he's=ˈhiːz she's=ˈʃiːz we're=ˈwɪr they're=ˈðɛr you've=ˈjuːv you'll=ˈjuːl that's=ˈðæts
                there's=ˈðɛrz what's=ˈwʌts let's=ˈlɛts ain't=ˈeɪnt doesn't=ˈdʌ.zənt haven't=ˈhæ.vənt hasn't=ˈhæ.zənt aren't=ˈaːrnt weren't=ˈwɜːrnt
                never=ˈnɛ.vɚ give=ˈgɪv desert=ˈdɛ.zɚt around=ə.ˈraʊnd strangers=ˈstreɪn.dʒɚz tears=ˈtɪrz hurt=ˈhɜːrt goodbye=gʊd.ˈbaɪ lie=ˈlaɪ
                eye=ˈaɪ tiger=ˈtaɪ.gɚ rising=ˈraɪ.zɪŋ thrill=ˈθrɪl burns=ˈbɜːrnz three=ˈθriː birds=ˈbɜːrdz worry=ˈwʌ.ri darling=ˈdaːr.lɪŋ
                fire=ˈfaɪr desire=dɪ.ˈzaɪr wild=ˈwaɪld child=ˈtʃaɪld burns=ˈbɜːrnz ring=ˈrɪŋ dynamite=ˈdaɪ.nə.maɪt lose=ˈluːz yourself=jɔːr.ˈsɛlf
                """;
        for (String entry : entries.strip().split("\\s+")) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                LEXICON.put(entry.substring(0, eq), Phonemizer.transcription(entry.substring(eq + 1)));
            }
        }
    }

    private EnglishPhonemizer() {
    }

    @Override
    public String decimalPoint() {
        return "point";
    }

    @Override
    public List<Syllable> word(String word) {
        List<Syllable> known = LEXICON.get(word);
        if (known != null) {
            return known;
        }
        // split off endings, look up the stem or read it by the rules
        for (String[] suffix : SUFFIXES) {
            String end = suffix[0];
            if (word.length() > end.length() + 2 && word.endsWith(end)) {
                String stem = word.substring(0, word.length() - end.length());
                List<Syllable> stemSyllables = LEXICON.get(stem);
                if (stemSyllables == null && stem.endsWith("i") && LEXICON.containsKey(stem.substring(0, stem.length() - 1) + "y")) {
                    stemSyllables = LEXICON.get(stem.substring(0, stem.length() - 1) + "y");
                }
                if (stemSyllables == null && LEXICON.containsKey(stem + "e")) {
                    stemSyllables = LEXICON.get(stem + "e");
                }
                if (stemSyllables != null) {
                    return withSuffix(stemSyllables, suffix[1]);
                }
            }
        }
        List<Phoneme> phonemes = new Walk(word).run();
        if (phonemes.isEmpty()) {
            return List.of();
        }
        List<List<Phoneme>> parts = Phonemizer.syllabify(phonemes, ONSETS);
        int stressed = stressedSyllable(word, parts.size());
        reduce(parts, stressed);
        return Phonemizer.stress(parts, stressed);
    }

    /** Ending → sounds; {@code ~} stands for "voiced/voiceless depending on the stem" (s/z, d/t/ɪd). */
    private static final String[][] SUFFIXES = {
            {"ing", "ɪŋ"}, {"ings", "ɪŋz"}, {"ed", "~ed"}, {"es", "~s"}, {"s", "~s"}, {"'s", "~s"}, {"ly", "li"}, {"er", "ɚ"}, {"ers", "ɚz"},
            {"est", "ɪst"}, {"ness", "nɪs"}, {"ment", "mənt"}, {"ful", "fəl"}, {"less", "lɪs"}, {"y", "i"},
    };

    private static List<Syllable> withSuffix(List<Syllable> stem, String suffix) {
        List<Syllable> result = new ArrayList<>(stem);
        Syllable last = Compat.last(result);
        Phoneme end = Compat.last(last.phonemes());
        if (suffix.equals("~s")) {
            if (end == S || end == Z || end == SH || end == ZH || end == CH || end == DJ) {
                result.add(new Syllable(List.of(I, Z), 0));
            } else {
                boolean voiced = end.isVoiced() || end.isVocalic();
                result.set(result.size() - 1, append(last, voiced ? Z : S));
            }
            return result;
        }
        if (suffix.equals("~ed")) {
            if (end == T || end == D) {
                result.add(new Syllable(List.of(I, D), 0));
            } else {
                boolean voiced = end.isVoiced() || end.isVocalic();
                result.set(result.size() - 1, append(last, voiced ? D : T));
            }
            return result;
        }
        List<Syllable> extra = Phonemizer.transcription(suffix);
        if (extra.size() == 1 && !Compat.first(Compat.first(extra).phonemes()).isVocalic() && Compat.first(extra).phonemes().stream().noneMatch(Phoneme::isVocalic)) {
            result.set(result.size() - 1, new Syllable(concat(last.phonemes(), Compat.first(extra).phonemes()), last.stress()));
        } else {
            result.addAll(extra);
        }
        return result;
    }

    private static Syllable append(Syllable syllable, Phoneme phoneme) {
        return new Syllable(concat(syllable.phonemes(), List.of(phoneme)), syllable.stress());
    }

    private static List<Phoneme> concat(List<Phoneme> a, List<Phoneme> b) {
        List<Phoneme> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static int stressedSyllable(String word, int syllables) {
        if (syllables < 2) {
            return 0;
        }
        for (String suffix : new String[]{"tion", "sion", "cian", "ic", "ical", "ity", "ify", "graphy", "logy"}) {
            if (word.endsWith(suffix)) {
                int suffixSyllables = suffix.equals("ical") || suffix.equals("ity") || suffix.equals("ify") || suffix.equals("logy") || suffix.equals("graphy") ? 2 : 1;
                return Math.max(0, syllables - suffixSyllables - 1);
            }
        }
        for (String prefix : new String[]{"a", "be", "re", "de", "pre", "pro", "com", "con", "ex", "un", "dis", "mis", "im", "in", "em", "en", "sub", "sur", "per", "for"}) {
            if (word.startsWith(prefix) && word.length() > prefix.length() + 3 && syllables > 1 && !word.startsWith(prefix + prefix.charAt(prefix.length() - 1))) {
                return 1;
            }
        }
        return 0;
    }

    /** Unstressed short vowels in middle syllables become schwa (not at the end of a word: happy, taxi). */
    private static void reduce(List<List<Phoneme>> parts, int stressed) {
        for (int s = 0; s < parts.size(); s++) {
            if (s == stressed || s == parts.size() - 1 && parts.size() > 1) {
                continue;
            }
            List<Phoneme> syllable = parts.get(s);
            for (int k = 0; k < syllable.size(); k++) {
                Phoneme p = syllable.get(k);
                if (p == A || p == AEE || p == E || p == O || p == AH || p == U) {
                    syllable.set(k, SCHWA);
                }
            }
        }
    }

    /** Walks through a word letter by letter. */
    private static final class Walk {
        private final String w;
        private final List<Phoneme> out = new ArrayList<>();
        private int i;

        Walk(String word) {
            this.w = word;
        }

        private static boolean isVowelLetter(char c) {
            return "aeiouy".indexOf(c) >= 0;
        }

        private boolean at(String text) {
            return w.startsWith(text, i);
        }

        private char ch(int offset) {
            int k = i + offset;
            return k >= 0 && k < w.length() ? w.charAt(k) : '\0';
        }

        private void add(Phoneme... phonemes) {
            for (Phoneme phoneme : phonemes) {
                out.add(phoneme);
            }
        }

        List<Phoneme> run() {
            while (i < w.length()) {
                char c = w.charAt(i);
                if (c == '\'') {
                    i++;
                    continue;
                }
                if (isVowelLetter(c) && !(c == 'y' && i == 0)) {
                    vowel();
                } else {
                    consonant();
                }
            }
            return out;
        }

        private boolean silentFinalE(int from) {
            // silent final e: consonant + e at the end of a word (not "le": little → əl)
            return from == w.length() - 1 && w.charAt(from) == 'e' && from > 0 && !isVowelLetter(w.charAt(from - 1)) && out.stream().anyMatch(Phoneme::isVocalic);
        }

        private void vowel() {
            char c = ch(0);
            char n = ch(1);
            char nn = ch(2);
            boolean last = i == w.length() - 1;
            // endings
            if (c == 'e' && last && !out.isEmpty() && out.stream().anyMatch(Phoneme::isVocalic)) {
                // silent e (name) – but "le" at the end (table) = əl
                if (ch(-1) == 'l' && !out.isEmpty() && Compat.last(out) == L && w.length() > 2 && !isVowelLetter(ch(-2))) {
                    Compat.removeLast(out);
                    add(SCHWA, L);
                }
                i++;
                return;
            }
            if (c == 'y' && last) {
                add(w.length() <= 3 ? AI : II);
                i++;
                return;
            }
            if (at("igh")) {
                add(AI);
                i += 3;
                return;
            }
            if (at("ough")) {
                add(AW);
                i += 4;
                return;
            }
            if (at("augh")) {
                add(AW);
                i += 4;
                return;
            }
            // digraphs
            String two = c + String.valueOf(n);
            Phoneme digraph = switch (two) {
                case "ee", "ea" -> II;
                case "oo" -> nn == 'k' || nn == 'd' ? U : UU;
                case "ou" -> nn == 'r' ? AW : AU;
                case "ow" -> last || nn == '\0' || nn == 'n' || nn == 'l' || nn == 's' || nn == 'e' ? OU : AU;
                case "oa" -> OU;
                case "ai", "ay", "ei", "ey" -> EI;
                case "oi", "oy" -> OI;
                case "ie" -> nn == '\0' ? AI : II;
                case "au", "aw" -> AW;
                case "ew", "ue", "ui" -> UU;
                case "ar" -> AA;
                case "or" -> AW;
                case "er", "ir", "ur" -> isVowelLetter(nn) && two.equals("er") ? null : ERR;
                default -> null;
            };
            if (digraph != null) {
                add(digraph);
                i += 2;
                if ((two.equals("ar") || two.equals("or")) && !isVowelLetter(ch(0))) {
                    add(R);
                }
                if (two.equals("ar") || two.equals("or")) {
                    // "are", "ore": silent e
                    if (ch(0) == 'e' && i == w.length() - 1) {
                        i++;
                    }
                }
                return;
            }
            // a single vowel: open (consonant + vowel / silent final e follows) or closed
            int consonants = 0;
            int k = i + 1;
            while (k < w.length() && !isVowelLetter(w.charAt(k))) {
                if (w.startsWith("ch", k) || w.startsWith("sh", k) || w.startsWith("th", k) || w.startsWith("ck", k) || w.startsWith("ph", k) || w.startsWith("wh", k)) {
                    k += 2;
                } else {
                    k++;
                }
                consonants++;
            }
            boolean magicE = consonants == 1 && k == w.length() - 1 && w.charAt(k) == 'e';
            boolean open = consonants == 0 && k < w.length() || consonants == 1 && k < w.length() && !last && !(k == w.length() - 1 && w.charAt(k) == 'y' && consonants == 1 && w.length() <= 4)
                    || magicE;
            if (consonants == 0 && k >= w.length() && !last) {
                open = true;
            }
            // before r: "fire", "care", "here" (vowel + r + e)
            Phoneme chosen = switch (c) {
                case 'a' -> open ? EI : AEE;
                case 'e' -> open ? II : E;
                case 'i', 'y' -> open ? AI : I;
                case 'o' -> open ? OU : O;
                case 'u' -> open ? UU : AH;
                default -> SCHWA;
            };
            if (c == 'a' && n == 'l' && (nn == 'l' || nn == 'k' || nn == 't')) {
                chosen = AW;
            }
            if (c == 'a' && (n == 'w' || n == 'u')) {
                chosen = AW;
            }
            if (c == 'o' && n == 'l' && nn == 'd') {
                chosen = OU;
            }
            if (c == 'i' && n == 'n' && nn == 'd') {
                chosen = AI;
            }
            if (c == 'u' && (ch(-1) == 'p' || ch(-1) == 'b' || ch(-1) == 'f') && !open) {
                chosen = U;
            }
            add(chosen);
            i++;
        }

        private void consonant() {
            char c = ch(0);
            char n = ch(1);
            boolean beforeVowel = isVowelLetter(n) && !silentFinalE(i + 1);
            if (at("tch")) {
                add(CH);
                i += 3;
                return;
            }
            if (at("dge")) {
                add(DJ);
                i += 3;
                return;
            }
            if (at("tion") || at("sion") && !out.isEmpty() && !Compat.last(out).isVocalic() || at("cian")) {
                add(at("sion") ? ZH : SH, SCHWA, N);
                i += 4;
                return;
            }
            if (at("sion")) {
                add(ZH, SCHWA, N);
                i += 4;
                return;
            }
            if (at("ture")) {
                add(CH, ER);
                i += 4;
                return;
            }
            if (at("sure")) {
                add(ZH, ER);
                i += 4;
                return;
            }
            if (at("ch")) {
                add(CH);
                i += 2;
                return;
            }
            if (at("sh")) {
                add(SH);
                i += 2;
                return;
            }
            if (at("th")) {
                add(i == 0 ? TH : DH);
                i += 2;
                return;
            }
            if (at("ph")) {
                add(F);
                i += 2;
                return;
            }
            if (at("wh")) {
                add(n == 'o' ? H : W);
                i += 2;
                return;
            }
            if (at("ck")) {
                add(K);
                i += 2;
                return;
            }
            if (at("kn") && i == 0 || at("gn") && i == 0) {
                add(N);
                i += 2;
                return;
            }
            if (at("wr") && i == 0) {
                add(R);
                i += 2;
                return;
            }
            if (at("gh")) {
                // silent (night, high) – at the end of a word after au/ou often f (laugh, tough: in the dictionary)
                i += 2;
                return;
            }
            if (at("qu")) {
                add(K, W);
                i += 2;
                return;
            }
            if (at("ng") && !isVowelLetter(ch(2))) {
                add(NG);
                i += 2;
                return;
            }
            if (at("nk")) {
                add(NG, K);
                i += 2;
                return;
            }
            if (n == c && "bdfgklmnprstz".indexOf(c) >= 0) {
                i++;
                n = ch(1);
                beforeVowel = isVowelLetter(n);
            }
            switch (c) {
                case 'b' -> {
                    if (!(i == w.length() - 1 && ch(-1) == 'm')) {
                        add(B);
                    }
                }
                case 'c' -> add(n == 'e' || n == 'i' || n == 'y' ? S : K);
                case 'd' -> add(D);
                case 'f' -> add(F);
                case 'g' -> add((n == 'e' || n == 'i' || n == 'y') && i > 0 ? DJ : G);
                case 'h' -> add(H);
                case 'j' -> add(DJ);
                case 'k' -> add(K);
                case 'l' -> add(L);
                case 'm' -> add(M);
                case 'n' -> add(N);
                case 'p' -> add(P);
                case 'q' -> add(K);
                case 'r' -> add(R);
                case 's' -> {
                    boolean between = !out.isEmpty() && Compat.last(out).isVocalic() && beforeVowel;
                    boolean finalVoiced = i == w.length() - 1 && !out.isEmpty() && (Compat.last(out).isVocalic() || Compat.last(out).isVoiced());
                    add(between || finalVoiced ? Z : S);
                }
                case 't' -> add(T);
                case 'v' -> add(V);
                case 'w' -> add(W);
                case 'x' -> add(K, S);
                case 'y' -> add(Y);
                case 'z' -> add(Z);
                default -> {
                    // skip foreign characters
                }
            }
            i++;
        }
    }

    // ── Numbers ──

    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    @Override
    public String number(long value) {
        if (value < 0) {
            return "minus " + number(-value);
        }
        if (value < 20) {
            return ONES[(int) value];
        }
        if (value < 100) {
            int tens = (int) (value / 10);
            int ones = (int) (value % 10);
            return TENS[tens] + (ones == 0 ? "" : " " + ONES[ones]);
        }
        if (value < 1000) {
            long rest = value % 100;
            return ONES[(int) (value / 100)] + " hundred" + (rest == 0 ? "" : " " + number(rest));
        }
        if (value < 1_000_000) {
            long rest = value % 1000;
            return number(value / 1000) + " thousand" + (rest == 0 ? "" : " " + number(rest));
        }
        if (value < 1_000_000_000L) {
            long rest = value % 1_000_000;
            return number(value / 1_000_000) + " million" + (rest == 0 ? "" : " " + number(rest));
        }
        long rest = value % 1_000_000_000L;
        return number(value / 1_000_000_000L) + " billion" + (rest == 0 ? "" : " " + number(rest));
    }
}
