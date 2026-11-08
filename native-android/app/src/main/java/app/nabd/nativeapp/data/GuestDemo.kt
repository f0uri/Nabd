package app.nabd.nativeapp.data

/**
 * Trial-only demo content for guest mode ("دخول كزائر"): the app opens with
 * realistic Arabic sample data so every main screen renders with content
 * instead of empty states. Nothing here touches the server, a token, or a
 * real account — it lives only in memory for the guest session.
 */
object GuestDemo {
    private fun hoursAgo(hours: Long) = isoDate(System.currentTimeMillis() - hours * 3600_000L)
    private fun daysAgo(days: Long) = isoDate(System.currentTimeMillis() - days * 24 * 3600_000L)

    private val me = Person("guest-me", "زائر نبض", "guest", "كنتفرج على التصميم الجديد ديال نبض",
        avatar = null, friend = false, following = false, points = 120, verified = false, inviteCode = "GUEST-DEMO", joined = "2026-11-08")
    private val salma = Person("guest-salma", "سلمى العلوي", "salma", "كنبغي السفر والتصوير",
        avatar = null, friend = true, following = true, points = 340, verified = true, joined = "2026-09-12")
    private val youssef = Person("guest-youssef", "يوسف الإدريسي", "youssef", "مطور تطبيقات",
        avatar = null, friend = true, following = true, points = 210, verified = false, joined = "2026-10-02")
    private val amina = Person("guest-amina", "أمينة بناني", "amina", "قهوة وكتب",
        avatar = null, friend = false, following = true, points = 95, verified = false, joined = "2026-10-20")
    private val karim = Person("guest-karim", "كريم التازي", "karim", "كرة القدم والرياضة",
        avatar = null, friend = true, following = false, points = 180, verified = false, joined = "2026-08-15")
    private val layla = Person("guest-layla", "ليلى الفاسي", "layla", "فن وتصميم",
        avatar = null, friend = false, following = false, points = 60, verified = false, joined = "2026-11-01")

    private val people = listOf(salma, youssef, amina, karim, layla)

    private fun breakdown(like: Int, love: Int, wow: Int, haha: Int = 0) =
        mapOf("like" to like, "love" to love, "haha" to haha, "wow" to wow, "sad" to 0, "angry" to 0)

    private val posts = listOf(
        FeedPost("guest-post-1", salma, "الصباح فالجبال ديال الأطلس... ما كاين ما أحسن من الهوا النقي والهدوء. شكون جرب يطلع للجبل هاد الخريف؟",
            hoursAgo(2), image = null, ratio = 1f, myReaction = null, likes = 42,
            comments = listOf(
                Comment("guest-c1", youssef, "صور زوينة بزاف! فين هاد البلاصة بالضبط؟", 3, false, hoursAgo(2)),
                Comment("guest-c2", amina, "المنظر خيالي", 5, false, hoursAgo(1))),
            pinned = true, breakdown = breakdown(30, 8, 4)),
        FeedPost("guest-post-2", youssef, "ساليت أول نسخة من التطبيق ديالي. باقي بزاف ديال الخدمة ولكن الفرحة كبيرة. شكراً لكل واحد شجعني.",
            hoursAgo(5), image = null, ratio = 1f, myReaction = null, likes = 28,
            comments = listOf(Comment("guest-c3", salma, "مبروك! بالتوفيق", 6, false, hoursAgo(4))),
            breakdown = breakdown(20, 5, 0, 3)),
        FeedPost("guest-post-3", amina, "كتاب جديد بديتو اليوم. شكون قراه؟ يستاهل ولا لا؟",
            hoursAgo(8), image = null, ratio = 1f, myReaction = null, likes = 17,
            comments = emptyList(), breakdown = breakdown(12, 3, 2)),
        FeedPost("guest-post-4", karim, "الرجاء البيضاوي ربحات البارح 2-0! الأجواء كانت خيالية فالتيران",
            daysAgo(1), image = null, ratio = 1f, myReaction = null, likes = 56,
            comments = listOf(
                Comment("guest-c4", youssef, "ماتش ناضي!", 4, false, daysAgo(1)),
                Comment("guest-c5", layla, "مبروك للرجاء", 2, false, hoursAgo(20))),
            breakdown = breakdown(40, 10, 0, 6)),
        FeedPost("guest-post-5", layla, "رسمت هاد اللوحة البارح بالليل. الألوان المائية عندها سحر خاص",
            daysAgo(1), image = null, ratio = 1f, myReaction = null, likes = 23,
            comments = listOf(Comment("guest-c6", amina, "فنانة!", 7, false, hoursAgo(18))),
            breakdown = breakdown(15, 6, 2)),
        FeedPost("guest-post-6", me, "أهلاً! أنا زائر كنتفرج على التصميم الجديد ديال نبض. شنو رأيكم فيه؟",
            hoursAgo(1), image = null, ratio = 1f, myReaction = null, likes = 12,
            comments = listOf(Comment("guest-c7", salma, "مرحبا بيك!", 2, false, hoursAgo(1))),
            breakdown = breakdown(9, 3, 0)),
    )

    private val stories = listOf(
        StoryGroup(me, listOf(
            Story("guest-story-me-1", me, "كنجرب التصميم الجديد ديال نبض", null, hoursAgo(1)),
            Story("guest-story-me-2", me, "شنو رأيكم؟", null, hoursAgo(0))),
            seen = true),
        StoryGroup(salma, listOf(
            Story("guest-story-salma-1", salma, "الغروب من الشرفة", null, hoursAgo(2)),
            Story("guest-story-salma-2", salma, "قهوة المساء", null, hoursAgo(1))),
            seen = false),
        StoryGroup(karim, listOf(
            Story("guest-story-karim-1", karim, "التداريب ديال اليوم", null, hoursAgo(4))),
            seen = true),
    )

    private val threads = listOf(
        Thread("guest-thread-salma", salma, listOf(
            Message("guest-m1", "guest-salma", "سلام! شفتي التصميم الجديد؟", false, false, null, true, null, emptyMap(), hoursAgo(3)),
            Message("guest-m2", "guest-me", "سلام سلمى! باقي كنتفرج عليه، زوين بزاف", false, false, null, true, null, emptyMap(), hoursAgo(3)),
            Message("guest-m3", "guest-salma", "الريلزات عجبوني، خاصهوم غير شوية تحسينات", false, false, null, true, null, emptyMap(), hoursAgo(2)),
            Message("guest-m4", "guest-salma", "نتسناو النسخة النهائية", false, false, null, false, null, emptyMap(), hoursAgo(1)))),
        Thread("guest-thread-youssef", youssef, listOf(
            Message("guest-m5", "guest-youssef", "كيداير؟ غادي نتلاقاو غدا؟", false, false, null, true, null, emptyMap(), hoursAgo(5)),
            Message("guest-m6", "guest-me", "واخا، مع 6 فالقهوة ديال الحي", false, false, null, true, null, emptyMap(), hoursAgo(5)),
            Message("guest-m7", "guest-youssef", "صافي، متفقين", false, false, null, true, null, emptyMap(), hoursAgo(4)))),
    )

    private val notifications = listOf(
        Notification("guest-n1", "like", salma, "guest-post-1", hoursAgo(2), read = false),
        Notification("guest-n2", "comment", youssef, "guest-post-2", hoursAgo(4), read = false),
        Notification("guest-n3", "follow", amina, null, hoursAgo(6), read = true),
    )

    private val reels = listOf(
        ReelItem("nb:guest-reel-1", REEL_SOURCE_LINK, "https://www.instagram.com/reel/DfTsk_yFgsg/", "", "", "سلمى العلوي", salma,
            "منظر الغروب من الجبال", hoursAgo(3), pulses = 128, pulse = null,
            comments = listOf(
                ReelComment("guest-rc1", youssef, "زوين بزاف!", hoursAgo(2)),
                ReelComment("guest-rc2", amina, "روعة", hoursAgo(1)))),
        ReelItem("nb:guest-reel-2", REEL_SOURCE_LINK, "https://www.instagram.com/reel/DXmZ_0-D7oe/", "", "", "كريم التازي", karim,
            "أحسن لقطات الماتش", hoursAgo(5), pulses = 64, pulse = null,
            comments = listOf(ReelComment("guest-rc3", layla, "ماتش ناضي!", hoursAgo(4)))),
    )

    fun snapshot(): Snapshot {
        val now = System.currentTimeMillis()
        return Snapshot(
            me = me,
            posts = posts,
            people = people,
            threads = threads,
            social = SocialGraph(
                followers = mapOf("guest-me" to 128, "guest-salma" to 342, "guest-youssef" to 215, "guest-amina" to 98, "guest-karim" to 187, "guest-layla" to 76),
                following = mapOf("guest-me" to 3, "guest-salma" to 210, "guest-youssef" to 180, "guest-amina" to 145, "guest-karim" to 160, "guest-layla" to 90),
                edges = mapOf(
                    "guest-me" to listOf("guest-salma", "guest-youssef", "guest-amina"),
                    "guest-salma" to listOf("guest-me", "guest-youssef"),
                    "guest-youssef" to listOf("guest-me", "guest-salma"),
                    "guest-amina" to listOf("guest-me"),
                    "guest-karim" to listOf("guest-me"),
                    "guest-layla" to emptyList())),
            announcements = listOf(
                Announcement("guest-ann-1", "مرحباً بك في نبض! هادي نسخة تجريبية للتصميم الجديد — جرّب كل شيء بحرية، وماشي حساب حقيقي.", hoursAgo(0))),
            stories = stories,
            notifications = notifications,
            settings = UserSettings(),
            blocked = emptyList(),
            devices = listOf(Device("guest-device", "هاد الجهاز", now, current = true)),
            postPrivacy = emptyMap(),
            highlights = mapOf("guest-me" to listOf(
                StoryHighlight("guest-hl-1", "ذكريات", listOf(
                    HighlightItem("guest-hli-1", "أول قصة", null, daysAgo(2)),
                    HighlightItem("guest-hli-2", "الغروب", null, daysAgo(1)))))),
            polls = emptyMap(),
            muted = emptySet(),
            reels = reels,
            reelPulses = mapOf("nb:guest-reel-1" to 128, "nb:guest-reel-2" to 64),
            reelComments = mapOf(
                "nb:guest-reel-1" to listOf(
                    ReelComment("guest-rc1", youssef, "زوين بزاف!", hoursAgo(2)),
                    ReelComment("guest-rc2", amina, "روعة", hoursAgo(1))),
                "nb:guest-reel-2" to listOf(
                    ReelComment("guest-rc3", layla, "ماتش ناضي!", hoursAgo(4)))),
            myReelPulses = emptyMap(),
        )
    }
}
