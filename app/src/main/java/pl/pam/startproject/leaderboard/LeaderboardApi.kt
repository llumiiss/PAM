package pl.pam.startproject.leaderboard

import retrofit2.http.GET

data class LeaderboardRowDto(
    val id: Long,
    val modeLabel: String,
    val durationMs: Long,
    val maxSpeedKmh: Double,
    val username: String?
)

data class LeaderboardResponseDto(
    val top1km: List<LeaderboardRowDto>,
    val top0100: List<LeaderboardRowDto>
)

interface LeaderboardApi {
    @GET("api/leaderboard")
    suspend fun getLeaderboard(): LeaderboardResponseDto
}
