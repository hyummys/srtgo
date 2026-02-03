from __future__ import annotations

import asyncio
import logging

from .config import ADMIN_TG_TOKEN, ADMIN_TG_CHAT_ID

logger = logging.getLogger(__name__)

_bot_app = None


async def send_approval_request(user_id: int, username: str, nickname: str):
    """Send a Telegram message to admin with approve/reject inline buttons."""
    if not ADMIN_TG_TOKEN or not ADMIN_TG_CHAT_ID:
        logger.info("Admin Telegram not configured, skipping approval notification")
        return

    try:
        import telegram
        from telegram import InlineKeyboardButton, InlineKeyboardMarkup

        bot = telegram.Bot(token=ADMIN_TG_TOKEN)
        keyboard = InlineKeyboardMarkup([
            [
                InlineKeyboardButton("승인", callback_data=f"approve:{user_id}"),
                InlineKeyboardButton("거절", callback_data=f"reject:{user_id}"),
            ]
        ])
        text = (
            f"🆕 새 회원가입 요청\n\n"
            f"아이디: {username}\n"
            f"닉네임: {nickname}\n"
            f"사용자 ID: {user_id}"
        )
        await bot.send_message(chat_id=ADMIN_TG_CHAT_ID, text=text, reply_markup=keyboard)
    except Exception as e:
        logger.error(f"Failed to send Telegram approval request: {e}")


async def _handle_callback(update, context):
    """Handle inline keyboard callback from admin."""
    from .database import update_user_status

    query = update.callback_query
    if not query or not query.data:
        return

    # Verify it's from the admin chat
    if str(query.message.chat.id) != str(ADMIN_TG_CHAT_ID):
        await query.answer("권한 없음")
        return

    data = query.data
    if ":" not in data:
        return

    action, uid_str = data.split(":", 1)
    try:
        user_id = int(uid_str)
    except ValueError:
        return

    if action == "approve":
        await update_user_status(user_id, "approved")
        await query.answer("승인 완료")
        await query.edit_message_text(query.message.text + "\n\n✅ 승인됨")
    elif action == "reject":
        await update_user_status(user_id, "rejected")
        await query.answer("거절 완료")
        await query.edit_message_text(query.message.text + "\n\n❌ 거절됨")


async def start_polling_bot():
    """Start the Telegram bot polling in the background. Call during app lifespan."""
    global _bot_app

    if not ADMIN_TG_TOKEN or not ADMIN_TG_CHAT_ID:
        logger.info("Admin Telegram bot not configured, skipping bot start")
        return

    try:
        from telegram.ext import Application, CallbackQueryHandler

        _bot_app = Application.builder().token(ADMIN_TG_TOKEN).build()
        _bot_app.add_handler(CallbackQueryHandler(_handle_callback))

        await _bot_app.initialize()
        await _bot_app.start()
        await _bot_app.updater.start_polling(drop_pending_updates=True)
        logger.info("Admin Telegram bot started polling")

        # Keep running until cancelled
        try:
            while True:
                await asyncio.sleep(3600)
        except asyncio.CancelledError:
            pass
    except Exception as e:
        logger.error(f"Telegram bot error: {e}")
    finally:
        if _bot_app:
            try:
                await _bot_app.updater.stop()
                await _bot_app.stop()
                await _bot_app.shutdown()
            except Exception:
                pass
