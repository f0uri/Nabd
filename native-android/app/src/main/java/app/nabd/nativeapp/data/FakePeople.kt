package app.nabd.nativeapp.data

import kotlin.random.Random

/**
 * Generated people: names, bios and posts, all deterministic from the
 * person's identity. Names are English, Korean (Hangul), Chinese (Hanzi)
 * or Japanese (kanji), with a Latin username derived from the romanization
 * where the name is not Latin. Bios of East Asian named people mention an
 * East Asian city. Nothing here is a real account and no photo comes from
 * the network: avatars are drawn in-app (see FakeAvatar). The same identity
 * always yields the same name, bio, avatar and posts, so a generated
 * person looks the same every time the app opens their list or profile.
 * A generated person is also a living account on their own: their profile
 * shows a seeded follower count, their posts carry seeded reaction counts,
 * and opening a generated post's reactors shows generated reactors too.
 * Every picture is unique: avatars are drawn in-app (see FakeAvatar) from
 * a ~6.9M-combination style space, permuted per list so no two people in
 * the same list ever share a face, and photo-post and comment pictures are
 * landscapes drawn in-app (see FakeScene), one scene per post or comment.
 * Nothing is a real photo and nothing comes from the network, so no
 * picture belongs to anyone else and no picture repeats.
 *
 * Identity: id "fake:<namespace>:<index>". Namespaces are
 * "post:<postId>:<kind>" for reactors of a post, "followers:<personId>"
 * for followers of an account and "following:<personId>" for the accounts
 * a generated person follows. The index is the person's number inside
 * that namespace, so the first N people stay the same as the count grows.
 * Generated posts are "fake-post:<personId>:<hourStart>" — the same hour
 * and the same person always give the same post.
 */
object FakePeople {
    /** How many generated people a list shows before "و N آخرون". */
    const val MAX_SHOWN = 50

    private val FIRST = listOf("James", "Mary", "John", "Emma", "Liam", "Olivia", "Noah", "Ava",
        "Lucas", "Mia", "Ethan", "Sophia", "Mason", "Isabella", "Logan", "Amelia", "Jack", "Harper",
        "Henry", "Ella", "Owen", "Grace", "Leo", "Chloe", "Ryan", "Lily", "Adam", "Zoe", "Nathan",
        "Nora", "Dylan", "Aria", "Caleb", "Luna", "Isaac", "Maya", "Aaron", "Layla", "Eli", "Freya",
        "Jonah", "Ivy", "Miles", "Ruby", "Felix", "Clara", "Oscar", "Elena", "Hugo", "Nadia")
    private val LAST = listOf("Smith", "Johnson", "Brown", "Taylor", "Miller", "Davis", "Garcia",
        "Wilson", "Moore", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin", "Thompson",
        "Young", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores", "Green", "Adams",
        "Baker", "Nelson", "Carter", "Mitchell", "Perez", "Roberts", "Turner", "Phillips", "Campbell",
        "Parker", "Evans", "Edwards", "Collins", "Stewart", "Sanchez", "Morris", "Rogers", "Reed",
        "Cook", "Morgan", "Bell", "Murphy")
    /** Korean names: Hangul family + given, with their Latin romanization for the username. */
    private val KO_FAMILY = listOf("김" to "kim", "이" to "lee", "박" to "park", "최" to "choi",
        "정" to "jung", "강" to "kang", "조" to "cho", "윤" to "yoon", "신" to "shin", "한" to "han",
        "오" to "oh", "서" to "seo", "권" to "kwon", "황" to "hwang", "안" to "ahn", "송" to "song",
        "류" to "ryu", "홍" to "hong")
    private val KO_GIVEN = listOf("민준" to "minjun", "서준" to "seojun", "지훈" to "jihoon",
        "현우" to "hyunwoo", "동혁" to "donghyuk", "성민" to "seongmin", "재현" to "jaehyun",
        "태양" to "taeyang", "은우" to "eunwoo", "하은" to "haeun", "지우" to "jiwoo",
        "서연" to "seoyeon", "예린" to "yerin", "수아" to "sua", "지민" to "jimin",
        "하린" to "harin", "나은" to "naeun", "채원" to "chaewon")
    /** Chinese names: Hanzi family + given, with pinyin for the username. */
    private val ZH_FAMILY = listOf("李" to "li", "王" to "wang", "张" to "zhang", "刘" to "liu",
        "陈" to "chen", "杨" to "yang", "黄" to "huang", "周" to "zhou", "吴" to "wu", "徐" to "xu",
        "孙" to "sun", "胡" to "hu", "朱" to "zhu", "高" to "gao", "林" to "lin", "何" to "he",
        "郭" to "guo", "马" to "ma")
    private val ZH_GIVEN = listOf("伟" to "wei", "芳" to "fang", "娜" to "na", "敏" to "min",
        "静" to "jing", "强" to "qiang", "磊" to "lei", "洋" to "yang", "艳" to "yan",
        "杰" to "jie", "涛" to "tao", "明" to "ming", "超" to "chao", "雪" to "xue",
        "晨" to "chen", "子豪" to "zihao", "欣怡" to "xinyi", "雨泽" to "yuze")
    /** Japanese names: kanji family + given, with romaji for the username. */
    private val JA_FAMILY = listOf("佐藤" to "sato", "鈴木" to "suzuki", "高橋" to "takahashi",
        "田中" to "tanaka", "渡辺" to "watanabe", "伊藤" to "ito", "山本" to "yamamoto",
        "中村" to "nakamura", "小林" to "kobayashi", "加藤" to "kato", "吉田" to "yoshida",
        "山田" to "yamada", "佐々木" to "sasaki", "山口" to "yamaguchi", "斎藤" to "saito",
        "松本" to "matsumoto", "井上" to "inoue", "木村" to "kimura")
    private val JA_GIVEN = listOf("健" to "ken", "翔" to "sho", "大輔" to "daisuke", "悠斗" to "yuto",
        "陽菜" to "hina", "美咲" to "misaki", "結衣" to "yui", "蓮" to "ren", "颯太" to "sota",
        "凛" to "rin", "悠真" to "yuma", "咲良" to "sakura", "海斗" to "kaito", "心愛" to "kokoa",
        "大翔" to "hiroto", "陽翔" to "haruto", "葵" to "aoi", "涼介" to "ryosuke")
    private val BIOS = listOf("Coffee first, then the world.", "Chasing sunsets and good vibes.",
        "Digital creator. Dreamer. Doer.", "Just here for the good content.",
        "Wanderer with a camera.", "Gym, books and late-night talks.",
        "Music is my second language.", "Food lover. Travel addict.",
        "Making every day count.", "Photography enthusiast.", "Tech nerd and proud of it.",
        "Weekend explorer.", "Cat person. Dog person. Both.",
        "Learning something new every day.", "Old soul, young heart.",
        "Coffee, code and calm.", "Collector of small moments.",
        "Here to inspire and be inspired.", "Simple life, big dreams.",
        "Always one song away from a good mood.", "Nature lover, city dweller.",
        "Storyteller in progress.", "Kindness costs nothing.",
        "Living life one post at a time.")
    /** Bios that place a generated person in an East Asian city, picked by seed. */
    private val ASIAN_BIOS = listOf("Seoul nights, coffee mornings.",
        "Tokyo-based dreamer.", "Shanghai foodie, always hungry.",
        "Living between Seoul and the world.", "Osaka street food enthusiast.",
        "Beijing in my heart, everywhere else in my feet.",
        "Busan sea breeze and slow days.", "Kyoto temples and quiet mornings.",
        "Taipei nights, bubble tea days.", "Hong Kong skyline lover.",
        "Chasing cherry blossoms every spring.",
        "Seoul subway stories, one stop at a time.")
    /** Generated post texts: (original, Arabic translation). The translation is pre-written, no service needed. */
    private val POSTS = listOf("Morning coffee hits different today." to "قهوة الصباح لها طعم مختلف اليوم.",
        "Golden hour never disappoints." to "الساعة الذهبية لا تخيّب أبداً.",
        "Finally finished the book I started months ago." to "أخيراً أنهيت الكتاب الذي بدأته قبل أشهر.",
        "This view deserves a post." to "هذا المنظر يستحق منشوراً.",
        "Sunday reset: clean room, clear mind." to "إعادة ضبط الأحد: غرفة نظيفة وعقل صافٍ.",
        "Found a tiny café with the best playlist." to "وجدت مقهى صغيراً بأفضل قائمة أغانٍ.",
        "Rainy days are for slow music and warm tea." to "الأيام الممطرة للموسيقى الهادئة والشاي الدافئ.",
        "New personal record at the gym today." to "رقم قياسي شخصي جديد في النادي اليوم.",
        "The city looks unreal after the rain." to "المدينة تبدو خيالية بعد المطر.",
        "Cooked my grandmother's recipe tonight." to "طبخت وصفة جدتي الليلة.",
        "Some days are made for long walks." to "بعض الأيام خُلقت للمشي الطويل.",
        "Just watched the sunset from the rooftop." to "شاهدت الغروب من السطح للتو.",
        "Learning to play the guitar, day twelve." to "أتعلّم العزف على الغيتار، اليوم الثاني عشر.",
        "This song has been on repeat all week." to "هذه الأغنية تتكرر طوال الأسبوع.",
        "A quiet evening with a good film." to "أمسية هادئة مع فيلم جيد.",
        "The market was full of colors today." to "السوق كان مليئاً بالألوان اليوم.",
        "Finished a 10k run before breakfast." to "أنهيت جري 10 كيلومترات قبل الفطور.",
        "Nothing beats homemade bread." to "لا شيء يضاهي الخبز المصنوع في البيت.",
        "Planning my next trip already." to "أخطط لرحلتي القادمة من الآن.",
        "The stars were incredible last night." to "النجوم كانت مذهلة الليلة الماضية.",
        "A new sketch, still a work in progress." to "رسم جديد، ما زال قيد العمل.",
        "Found my old photos from 2019." to "وجدت صوري القديمة من 2019.",
        "Today I chose kindness over being right." to "اليوم اخترت اللطف على أن أكون على حق.",
        "The garden is blooming again." to "الحديقة تزهر من جديد.",
        "Started a new series and I am hooked." to "بدأت مسلسلاً جديداً وأدمنته.",
        "Fresh haircut, fresh mindset." to "قصة شعر جديدة، وعقلية جديدة.",
        "Baked cookies and the whole house smells amazing." to "خبزت البسكويت والبيت كله تفوح منه رائحة رائعة.",
        "A rainy window and a warm blanket." to "نافذة ممطرة وبطانية دافئة.",
        "Met an old friend after years." to "قابلت صديقاً قديماً بعد سنوات.",
        "This quote stuck with me all day." to "هذه المقولة بقيت في ذهني طوال اليوم.",
        "Training hard, staying humble." to "أتدرب بجد وأبقى متواضعاً.",
        "The beach at 6am is a different world." to "الشاطئ في السادسة صباحاً عالم مختلف.",
        "Just planted my first herb garden." to "زرعت أول حديقة أعشاب لي للتو.",
        "A good book and a quiet corner." to "كتاب جيد وركن هادئ.",
        "Made it to the top of the hill." to "وصلت إلى قمة التل.",
        "Simple dinner, great company." to "عشاء بسيط وصحبة رائعة.")
    /**
     * Place names for generated photo posts: real places a generated
     * person "visited". The name is only the location label; the picture
     * itself is a landscape drawn in-app (see FakeScene), one scene per
     * post, so the label and the picture always travel together but the
     * picture never repeats. (The old bundled place photos in
     * assets/places are no longer referenced.)
     */
    val PLACE_NAMES = listOf("Seoul", "Tokyo", "Paris", "Casablanca",
        "Casablanca Beach", "Atlas Mountains", "Merzouga Desert", "Paris Café",
        "Central Park", "Alps", "Marrakech", "Chefchaouen", "Santorini",
        "Venice", "New York", "London", "Dubai", "Kyoto", "Rome", "Barcelona",
        "Swiss Lake", "Istanbul", "Bali", "Amalfi Coast")
    /** Nice comment phrases per language: (original, Arabic translation). */
    private val COMMENT_EN = listOf("Beautiful post!" to "منشور جميل!", "Love this!" to "أحب هذا!",
        "So inspiring!" to "ملهم جداً!", "Amazing work!" to "عمل رائع!",
        "This made my day." to "هذا أسعد يومي.", "Wonderful!" to "رائع!",
        "Keep it up!" to "واصل!", "So creative!" to "مبدع جداً!",
        "I really like this." to "أعجبني هذا كثيراً.", "Great vibes!" to "أجواء رائعة!",
        "This is so nice." to "هذا جميل جداً.", "Well done!" to "أحسنت!")
    private val COMMENT_KO = listOf("멋져요!" to "رائع!", "정말 좋아요!" to "أعجبني كثيراً!",
        "감동적이에요." to "مؤثر جداً.", "대단해요!" to "مذهل!",
        "오늘 하루가 밝아졌어요." to "أضاء يومي.", "너무 예뻐요!" to "جميل جداً!",
        "계속 응원할게요!" to "سأواصل تشجيعك!", "창의적이네요!" to "مبدع!",
        "정말 마음에 들어요." to "أعجبني حقاً.", "분위기가 좋아요!" to "الأجواء جميلة!",
        "너무 좋네요." to "جميل جداً.", "잘했어요!" to "أحسنت!")
    private val COMMENT_ZH = listOf("太棒了！" to "رائع جداً!", "我很喜欢！" to "أحب هذا كثيراً!",
        "真鼓舞人心！" to "ملهم حقاً!", "做得太好了！" to "عمل ممتاز!",
        "今天心情变好了。" to "تحسّن مزاجي اليوم.", "太美了！" to "جميل جداً!",
        "继续加油！" to "واصل التقدم!", "很有创意！" to "مبدع جداً!",
        "我真的很喜欢。" to "أعجبني حقاً.", "氛围真好！" to "الأجواء رائعة!",
        "真不错。" to "جميل فعلاً.", "干得漂亮！" to "أحسنت صنعاً!")
    private val COMMENT_JA = listOf("素晴らしい！" to "رائع!", "大好きです！" to "أحبه كثيراً!",
        "とても感動しました。" to "تأثرت كثيراً.", "すごいですね！" to "مذهل!",
        "今日一日が明るくなりました。" to "أضاء يومي.", "とても綺麗です！" to "جميل جداً!",
        "応援しています！" to "أشجعك!", "クリエイティブですね！" to "مبدع!",
        "本当に気に入りました。" to "أعجبني حقاً.", "雰囲気がいいですね！" to "الأجواء جميلة!",
        "とてもいいですね。" to "جميل جداً.", "よくできました！" to "أحسنت!")

    /** Pre-written Arabic translation of any generated text, or null when the text is not generated. */
    private val ALL_TRANSLATIONS: Map<String, String> by lazy {
        (POSTS + COMMENT_EN + COMMENT_KO + COMMENT_ZH + COMMENT_JA).toMap()
    }
    fun translationFor(text: String): String? = ALL_TRANSLATIONS[text]

    /** One nice comment phrase in the person's own language, picked by seed. */
    fun commentPhraseFor(origin: Origin, seed: Long): Pair<String, String> {
        val pool = when (origin) {
            Origin.ENGLISH -> COMMENT_EN
            Origin.KOREAN -> COMMENT_KO
            Origin.CHINESE -> COMMENT_ZH
            Origin.JAPANESE -> COMMENT_JA
        }
        return pool[Random(seed).nextInt(pool.size)]
    }

    fun isFakeId(id: String): Boolean = id.startsWith("fake:")
    fun isFakePostId(id: String): Boolean = id.startsWith("fake-post:")

    /** Stable 64-bit seed for an identity string; the same id always seeds the same person. */
    fun seedOf(id: String): Long {
        var hash = -3750763034362895579L
        id.toByteArray(Charsets.UTF_8).forEach { byte ->
            hash = hash xor byte.toLong()
            hash *= 1099511628211L
        }
        return hash
    }

    /** Name origin of a generated person, fixed by their index in the namespace. */
    enum class Origin { ENGLISH, KOREAN, CHINESE, JAPANESE }

    /** Half of the people are English-named; the rest split evenly Korean, Chinese, Japanese. */
    fun originForIndex(index: Int): Origin = when (index % 6) {
        3 -> Origin.KOREAN
        4 -> Origin.CHINESE
        5 -> Origin.JAPANESE
        else -> Origin.ENGLISH
    }

    /** True when a generated id belongs to a Korean, Chinese or Japanese named person. */
    fun isAsianId(id: String): Boolean {
        if (!isFakeId(id)) return false
        val body = id.removePrefix("fake:")
        val split = body.lastIndexOf(':')
        if (split <= 0) return false
        val index = body.substring(split + 1).toIntOrNull() ?: return false
        return originForIndex(index) != Origin.ENGLISH
    }

    /** The namespace index of a generated id, or null when the id is not generated. */
    private fun indexOf(id: String): Int? {
        if (!isFakeId(id)) return null
        val body = id.removePrefix("fake:")
        val split = body.lastIndexOf(':')
        if (split <= 0) return null
        return body.substring(split + 1).toIntOrNull()
    }

    /** Given-name position inside its pool, the same formula asianName() picks the name with. */
    private fun asianGivenIndex(index: Int, familySize: Int, givenSize: Int): Int {
        val sub = index / 6
        return (sub / familySize + sub) % givenSize
    }

    /** Male given-name positions per Asian pool (the rest of each pool is female). */
    private val ZH_MALE_GIVEN = setOf(0, 5, 6, 7, 9, 10, 11, 12, 14, 15, 17)
    private val JA_MALE_GIVEN = setOf(0, 1, 2, 3, 7, 8, 10, 12, 14, 15, 17)

    /** True when the generated person at [index] of [origin] has a male name. */
    fun isMaleIndex(index: Int, origin: Origin): Boolean = when (origin) {
        // The English first-name pool alternates male, female from index 0.
        Origin.ENGLISH -> (index % FIRST.size) % 2 == 0
        // The Korean given pool lists its 9 male names first, then 9 female.
        Origin.KOREAN -> asianGivenIndex(index, KO_FAMILY.size, KO_GIVEN.size) < 9
        Origin.CHINESE -> asianGivenIndex(index, ZH_FAMILY.size, ZH_GIVEN.size) in ZH_MALE_GIVEN
        Origin.JAPANESE -> asianGivenIndex(index, JA_FAMILY.size, JA_GIVEN.size) in JA_MALE_GIVEN
    }

    // No bundled photo portraits for generated people any more: 16 photos
    // shared by a million people meant every photo repeated thousands of
    // times. Every generated person now shows the drawn avatar (see
    // FakeAvatar), unique per person. The files in assets/faces stay in
    // the APK unreferenced; real accounts keep their own photos untouched.

    /** The generated person for a namespace and index; pure and deterministic. */
    fun person(namespace: String, index: Int): Person {
        val id = "fake:$namespace:$index"
        val seed = seedOf(id)
        val random = Random(seed)
        val (displayName, username) = when (originForIndex(index)) {
            Origin.ENGLISH -> {
                val first = FIRST[index % FIRST.size]
                val last = LAST[(index / FIRST.size + index) % LAST.size]
                val user = buildString {
                    append(first.lowercase()).append('.').append(last.lowercase())
                    if (index >= FIRST.size * LAST.size) append(index / (FIRST.size * LAST.size) + 1)
                }
                "$first $last" to user
            }
            Origin.KOREAN -> asianName(index, KO_FAMILY, KO_GIVEN)
            Origin.CHINESE -> asianName(index, ZH_FAMILY, ZH_GIVEN)
            Origin.JAPANESE -> asianName(index, JA_FAMILY, JA_GIVEN)
        }
        val bio = if (originForIndex(index) == Origin.ENGLISH) BIOS[random.nextInt(BIOS.size)]
            else ASIAN_BIOS[random.nextInt(ASIAN_BIOS.size)]
        return Person(id, displayName, username, bio, null, false, false,
            points = random.nextInt(0, 900), fakeSeed = seed)
    }

    /**
     * One Asian name from a (native, latin) pool pair: the display name is
     * family + given in the native script, the username is the Latin
     * romanization, so it stays typable as @username.
     */
    private fun asianName(index: Int, family: List<Pair<String, String>>, given: List<Pair<String, String>>): Pair<String, String> {
        val sub = index / 6
        val fam = family[sub % family.size]
        val giv = given[(sub / family.size + sub) % given.size]
        val user = buildString {
            append(fam.second).append('.').append(giv.second)
            if (sub >= family.size * given.size) append(sub / (family.size * given.size) + 1)
        }
        return "${fam.first}${giv.first}" to user
    }

    /** Rebuild a generated person from their id, or null when the id is not a generated one. */
    fun personFromFakeId(id: String): Person? {
        if (!isFakeId(id)) return null
        val body = id.removePrefix("fake:")
        val split = body.lastIndexOf(':')
        if (split <= 0) return null
        val index = body.substring(split + 1).toIntOrNull() ?: return null
        if (index < 0) return null
        return person(body.substring(0, split), index)
    }

    /** Seeded follower count shown on a generated person's own profile. */
    fun followerCount(person: Person): Int = Random(person.fakeSeed ?: seedOf(person.id)).nextInt(24, 2400)

    /**
     * Whether a generated person shows the «متصل الآن» dot: seeded per
     * person and per hour, so the same person stays online or offline
     * for a whole hour and the dot never flickers between polls.
     * About two in three generated people are online in any hour.
     */
    fun isOnline(person: Person, nowMs: Long): Boolean {
        if (person.fakeSeed == null) return false
        val hour = nowMs / 3_600_000L
        return Random(seedOf(person.id) xor hour).nextInt(3) != 0
    }

    /** The trending posts: the most reacted-to posts first, up to five. */
    fun trendingPosts(posts: List<FeedPost>, limit: Int = 5): List<FeedPost> =
        posts.filter { it.likes > 0 || it.comments.isNotEmpty() || it.shares > 0 }
            .sortedWith(compareByDescending<FeedPost> { it.likes }.thenByDescending { it.comments.size }.thenByDescending { it.date })
            .take(limit)

    /** Seeded following count shown on a generated person's own profile. */
    fun followingCount(person: Person): Int = Random((person.fakeSeed ?: seedOf(person.id)) xor 0x5f3759dfL).nextInt(18, 900)

    /**
     * Reactors of a post: for every reaction kind, the stored breakdown
     * minus the real voters of that kind is generated people. Returns the
     * first [MAX_SHOWN] people with their kind, plus the total generated
     * count, so the list can end with "و N آخرون".
     */
    fun reactorsForPost(post: FeedPost): Pair<List<Pair<Person, String>>, Int> {
        val realByKind = post.voters.values.groupingBy { it }.eachCount()
        val shown = mutableListOf<Pair<Person, String>>()
        var total = 0
        REACTIONS.forEach { kind ->
            val generated = (post.breakdown[kind] ?: 0) - (realByKind[kind] ?: 0)
            if (generated <= 0) return@forEach
            total += generated
            val take = (MAX_SHOWN - shown.size).coerceAtLeast(0)
            repeat(minOf(generated, take)) { index -> shown += person("post:${post.id}:$kind", index) to kind }
        }
        return shown to total
    }

    /**
     * Followers of an account: the grown total minus the real followers is
     * generated people, seeded by follower number so the first N stay the
     * same as the count grows. Returns the first [MAX_SHOWN] plus the
     * total generated count.
     */
    fun followersForPerson(personId: String, totalFollowers: Int, realFollowerCount: Int): Pair<List<Person>, Int> {
        val generated = (totalFollowers - realFollowerCount).coerceAtLeast(0)
        val shown = (0 until minOf(generated, MAX_SHOWN)).map { person("followers:$personId", realFollowerCount + it) }
        return shown to generated
    }

    /** The accounts a generated person follows: seeded, deterministic, generated people only.
     * The total is the same seeded count shown on the profile, so the
     * counter and the list always agree. */
    fun followingForPerson(person: Person): Pair<List<Person>, Int> {
        val total = followingCount(person)
        val shown = (0 until minOf(total, MAX_SHOWN)).map { person("following:${person.id}", it) }
        return shown to total
    }

    /** Split [likes] over the six reaction kinds with a seeded random draw. */
    private fun spreadLikes(likes: Int, random: Random): Map<String, Int> {
        val kinds = List(likes) { REACTIONS[random.nextInt(REACTIONS.size)] }
        return REACTIONS.associateWith { kind -> kinds.count { it == kind } }
    }

    /**
     * The generated posts of one generated person, newest first. For each
     * of the past [hoursBack] hours there is a seeded ~25% chance of a
     * post, so the same hour and the same person always give the same
     * post. Half of the posts carry a generated landscape with its
     * location label and half are text only; posts live on the person's
     * profile and never enter the main feed.
     */
    fun postsForPerson(person: Person, nowMs: Long, hoursBack: Int = 72): List<FeedPost> {
        val thisHour = nowMs / 3_600_000L
        val posts = mutableListOf<FeedPost>()
        for (back in 0 until hoursBack) {
            val hourStart = (thisHour - back) * 3_600_000L
            buildPostForHour(person, hourStart, nowMs)?.let { posts += it }
        }
        return posts.sortedByDescending { it.date }
    }

    /** Rebuild one generated post from its id, or null when the id is not a generated post. */
    fun postById(id: String, nowMs: Long): FeedPost? {
        if (!isFakePostId(id)) return null
        val body = id.removePrefix("fake-post:")
        val split = body.lastIndexOf(':')
        if (split <= 0) return null
        val hourStart = body.substring(split + 1).toLongOrNull() ?: return null
        val person = personFromFakeId(body.substring(0, split)) ?: return null
        return buildPostForHour(person, hourStart, nowMs)
    }

    /**
     * One generated post for a person and an hour: ~25% of hours have a
     * post, and half of those carry a generated landscape with its
     * location label while the other half is text only. The caption always
     * comes from the phrase pool, so its Arabic translation stays available.
     */
    private fun buildPostForHour(person: Person, hourStart: Long, nowMs: Long): FeedPost? {
        val postSeed = seedOf("fake-post:${person.id}:$hourStart")
        val random = Random(postSeed)
        if (random.nextInt(4) != 0) return null
        val likes = random.nextInt(3, 140)
        val clean = spreadLikes(likes, random)
        val placeName = if (random.nextBoolean()) PLACE_NAMES[random.nextInt(PLACE_NAMES.size)] else null
        // Shares: a seeded base plus growth with the post's age, so a
        // generated post never sits at 0 مشاركة and older posts show more.
        val ageHours = ((nowMs - hourStart) / 3_600_000L).coerceAtLeast(0L).toInt()
        val shares = random.nextInt(5, 90) + ageHours * random.nextInt(1, 4)
        return FeedPost("fake-post:${person.id}:$hourStart", person, POSTS[random.nextInt(POSTS.size)].first,
            isoDate(hourStart), placeName?.let { FakeScene.sceneImage(postSeed) }, 1f, null, likes, emptyList(),
            breakdown = clean, location = placeName, shares = shares)
    }

    /** Generated comments appear on official posts: one new comment every few seconds of the post's age, up to 40. */
    const val FAKE_COMMENT_INTERVAL_MS = 5_000L
    const val FAKE_COMMENT_MAX = 40
    /** After the fast first 40, comments keep arriving slowly — one per hour of age — so old posts keep growing. */
    const val FAKE_COMMENT_SLOW_INTERVAL_MS = 3_600_000L
    /** A real comment is 10 seconds old before its auto-reply appears. */
    const val FAKE_AUTO_REPLY_DELAY_MS = 10_000L

    fun isFakeCommentId(id: String): Boolean = id.startsWith("fake-comment:")

    /**
     * The generated comments of an official post: a new one every few
     * seconds of the post's age up to 40, then one more per hour of age,
     * so an old post never freezes — its comments keep growing as time
     * passes — plus one auto-reply on each real root comment older than
     * 10 seconds. All client-side only — the ids are deterministic, so
     * the same post always shows the same comments and a pinned comment
     * keeps its pin. [extra] is the admin comment boost: that many more
     * generated comments on top of the age count, so a boosted post
     * shows them at once.
     */
    fun commentsForPost(post: FeedPost, nowMs: Long, pinnedIds: Set<String> = emptySet(), extra: Int = 0): List<Comment> {
        if (post.author.username != OFFICIAL_HANDLE) return emptyList()
        val postMs = runCatching { java.time.Instant.parse(post.date).toEpochMilli() }.getOrNull() ?: return emptyList()
        val ageMs = (nowMs - postMs).coerceAtLeast(0L)
        // Fast first 40 (one per 5s), then one per hour of age: an old post
        // never freezes at 40 — its comments keep growing as time passes.
        val fast = minOf(FAKE_COMMENT_MAX, (ageMs / FAKE_COMMENT_INTERVAL_MS).toInt() + 1)
        val slow = if (ageMs > FAKE_COMMENT_MAX * FAKE_COMMENT_INTERVAL_MS) ((ageMs - FAKE_COMMENT_MAX * FAKE_COMMENT_INTERVAL_MS) / FAKE_COMMENT_SLOW_INTERVAL_MS).toInt() + 1 else 0
        val count = minOf(FAKE_COMMENT_ABSOLUTE_MAX, fast + slow + extra.coerceAtLeast(0))
        val comments = (0 until count).map { index -> commentForIndex(post, index, postMs, nowMs, pinnedIds) }.toMutableList()
        post.comments.filter { it.replyTo == null && !isFakeId(it.author.id) }.forEachIndexed { realIndex, real ->
            val realMs = runCatching { java.time.Instant.parse(real.date).toEpochMilli() }.getOrNull() ?: return@forEachIndexed
            if (nowMs - realMs < FAKE_AUTO_REPLY_DELAY_MS) return@forEachIndexed
            comments += autoReplyFor(post, real, realIndex, realMs)
        }
        return comments
    }

    /** One generated root comment: nice words in the person's own language, a picture on about 1 in 3. */
    private fun commentForIndex(post: FeedPost, index: Int, postMs: Long, nowMs: Long, pinnedIds: Set<String>): Comment {
        val id = "fake-comment:${post.id}:$index"
        val seed = seedOf(id)
        val random = Random(seed)
        val person = person("comment:${post.id}", index)
        val (text, _) = commentPhraseFor(originForIndex(index), seed)
        val image = if (random.nextInt(3) == 0) FakeScene.sceneImage(seed) else null
        val pinned = id in pinnedIds
        val commentMs = if (index < FAKE_COMMENT_MAX) postMs + index * FAKE_COMMENT_INTERVAL_MS
            else postMs + FAKE_COMMENT_MAX * FAKE_COMMENT_INTERVAL_MS + (index - FAKE_COMMENT_MAX) * FAKE_COMMENT_SLOW_INTERVAL_MS
        val ageTicks = ((nowMs - commentMs) / FAKE_COMMENT_INTERVAL_MS).coerceAtLeast(0L)
        // Reactions grow with the comment's age; a pinned comment gains
        // them faster. Both are capped so old posts stay believable.
        val grown = minOf(if (pinned) ageTicks.toInt() else (ageTicks / 3).toInt(), if (pinned) 120 else 40)
        val likes = random.nextInt(0, 9) + grown
        return Comment(id, person, text, likes, false, isoDate(commentMs), null, image,
            spreadLikes(likes, random), null, pinned = pinned)
    }

    /** One auto-reply from a generated person on a real comment of an official post. */
    private fun autoReplyFor(post: FeedPost, real: Comment, realIndex: Int, realMs: Long): Comment {
        val id = "fake-comment-reply:${real.id}"
        val seed = seedOf(id)
        val random = Random(seed)
        val person = person("reply:${post.id}", realIndex)
        val (text, _) = commentPhraseFor(originForIndex(realIndex), seed)
        val likes = random.nextInt(0, 6)
        return Comment(id, person, text, likes, false, isoDate(realMs + FAKE_AUTO_REPLY_DELAY_MS), real.id, null,
            spreadLikes(likes, random), null)
    }

    /**
     * Reactors of a comment: the stored breakdown minus the real voters
     * of each kind is generated people. Returns the first [MAX_SHOWN]
     * people with their kind, plus the total generated count.
     */
    fun reactorsForComment(comment: Comment): Pair<List<Pair<Person, String>>, Int> {
        val realByKind = comment.voters.values.groupingBy { it }.eachCount()
        val shown = mutableListOf<Pair<Person, String>>()
        var total = 0
        REACTIONS.forEach { kind ->
            val generated = (comment.reactions[kind] ?: 0) - (realByKind[kind] ?: 0)
            if (generated <= 0) return@forEach
            total += generated
            val take = (MAX_SHOWN - shown.size).coerceAtLeast(0)
            repeat(minOf(generated, take)) { index -> shown += person("comment-react:${comment.id}:$kind", index) to kind }
        }
        return shown to total
    }
}
