use anchor_lang::prelude::*;
use anchor_lang::solana_program;
use anchor_spl::token::{self, Mint, Token, TokenAccount, Transfer};
use solana_instructions_sysvar::load_instruction_at_checked;

pub mod noop_program {
    anchor_lang::declare_id!("noopb9bkMVfRPU8AsbpTUg8AQkHtKwMYZiFUjNRtMmV");
}

pub mod compression_program {
    anchor_lang::declare_id!("cmtDvXumGCrqC1Age74AVPhSRVXJMd8PJS91L8KbNCK");
}

pub mod ed25519_program {
    anchor_lang::declare_id!("Ed25519SigVerify111111111111111111111111111");
}

pub mod ix_sysvar {
    anchor_lang::declare_id!("Sysvar1nstructions1111111111111111111111111");
}

declare_id!("x2HARL2kBx2jVHtgWvf8sX8zbZ3sBAvtHJeg8AgosqR");

#[program]
pub mod orbis_protocol {
    use super::*;

    pub fn initialize(
        ctx: Context<Initialize>,
        treasury: Pubkey,
        orbis_mint: Pubkey,
        write_fee: u64,
        fee_per_mb: u64,
        min_fee: u64,
        registration_fee: u64,
    ) -> Result<()> {
        let config = &mut ctx.accounts.config;
        config.admin = ctx.accounts.admin.key();
        config.treasury = treasury;
        config.orbis_mint = orbis_mint;
        config.merkle_tree = ctx.accounts.merkle_tree.key();
        config.write_fee = write_fee;
        config.fee_per_mb = fee_per_mb;
        config.min_fee = min_fee;
        config.registration_fee = registration_fee;
        Ok(())
    }

    pub fn update_config_tree(
        ctx: Context<UpdateConfigTree>,
        new_merkle_tree: Pubkey,
    ) -> Result<()> {
        let config = &mut ctx.accounts.config;
        config.merkle_tree = new_merkle_tree;
        Ok(())
    }

    pub fn update_config_fees(
        ctx: Context<UpdateConfigFees>,
        write_fee: u64,
        fee_per_mb: u64,
        min_fee: u64,
        registration_fee: u64,
    ) -> Result<()> {
        let config = &mut ctx.accounts.config;
        config.write_fee = write_fee;
        config.fee_per_mb = fee_per_mb;
        config.min_fee = min_fee;
        config.registration_fee = registration_fee;
        Ok(())
    }

    pub fn register_clone(ctx: Context<RegisterClone>, base_url: String) -> Result<()> {
        require!(base_url.len() <= 140, ErrorCode::UrlTooLong);

        let clone_info = &mut ctx.accounts.clone_info;
        let config = &ctx.accounts.config;

        clone_info.owner = ctx.accounts.signer.key();
        clone_info.base_url = base_url;
        clone_info.trust_score = 1000;
        clone_info.total_sync_count = 0;
        clone_info.flag_count = 0;
        clone_info.is_genesis = ctx.accounts.signer.key() == config.admin;

        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.signer_token_account.to_account_info(),
                    to: ctx.accounts.treasury_token_account.to_account_info(),
                    authority: ctx.accounts.signer.to_account_info(),
                },
            ),
            config.registration_fee,
        )?;

        msg!("Clone registered with fee: {}", config.registration_fee);
        Ok(())
    }

    pub fn update_clone_base_url(
        ctx: Context<UpdateCloneBaseUrl>,
        new_base_url: String,
    ) -> Result<()> {
        let clone_info = &mut ctx.accounts.clone_info;
        clone_info.base_url = new_base_url;
        Ok(())
    }

    pub fn sync_index_manifest(
        ctx: Context<SyncIndexManifest>,
        packet_type: u8,
        batch_id: u32,
        manifest_hash: [u8; 32],
        payload_hash: [u8; 32],
        manifest_pointer: String,
        entry_count: u32,
        action_count: u32,
        from_ts: i64,
        to_ts: i64,
    ) -> Result<()> {
        require!(manifest_pointer.len() <= 200, ErrorCode::BatchTooLarge);

        let merkle_tree = &ctx.accounts.merkle_tree;
        let config = &ctx.accounts.config;

        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.user_token_account.to_account_info(),
                    to: ctx.accounts.treasury_token_account.to_account_info(),
                    authority: ctx.accounts.clone_signer.to_account_info(),
                },
            ),
            config.write_fee,
        )?;

        let mut leaf_data = Vec::new();
        leaf_data.push(packet_type);
        leaf_data.extend_from_slice(&batch_id.to_le_bytes());
        leaf_data.extend_from_slice(&manifest_hash);
        leaf_data.extend_from_slice(&payload_hash);
        leaf_data.extend_from_slice(&entry_count.to_le_bytes());
        leaf_data.extend_from_slice(&action_count.to_le_bytes());
        leaf_data.extend_from_slice(&from_ts.to_le_bytes());
        leaf_data.extend_from_slice(&to_ts.to_le_bytes());

        let leaf_hash = solana_sha256_hasher::hash(&leaf_data).to_bytes();

        let mut data = Vec::with_capacity(40);
        let disc = solana_sha256_hasher::hash(b"global:append").to_bytes();
        data.extend_from_slice(&disc[..8]);
        data.extend_from_slice(&leaf_hash);

        let accounts = vec![
            AccountMeta::new(merkle_tree.key(), false),
            AccountMeta::new_readonly(config.key(), true),
            AccountMeta::new_readonly(ctx.accounts.noop_program.key(), false),
        ];

        let bump = [ctx.bumps.config];
        let seeds = &[b"global_config".as_ref(), bump.as_ref()];
        let signer_seeds = &[&seeds[..]];

        solana_program::program::invoke_signed(
            &solana_program::instruction::Instruction {
                program_id: ctx.accounts.compression_program.key(),
                accounts,
                data,
            },
            &[
                merkle_tree.to_account_info(),
                config.to_account_info(),
                ctx.accounts.noop_program.to_account_info(),
                ctx.accounts.compression_program.to_account_info(),
            ],
            signer_seeds,
        )?;

        emit!(IndexManifestCommitted {
            packet_type,
            batch_id,
            manifest_hash,
            payload_hash,
            manifest_pointer,
            entry_count,
            action_count,
            from_ts,
            to_ts,
            provider: ctx.accounts.clone_signer.key(),
        });

        Ok(())
    }

    pub fn init_streaming_escrow(ctx: Context<InitStreamingEscrow>, amount: u64) -> Result<()> {
        let escrow = &mut ctx.accounts.streaming_escrow;

        require!(
            ctx.accounts.requester.key() != ctx.accounts.provider.key(),
            ErrorCode::SelfPaymentNotAllowed
        );

        escrow.requester = ctx.accounts.requester.key();
        escrow.provider = ctx.accounts.provider.key();
        escrow.amount_locked = amount;
        escrow.amount_claimed = 0;
        escrow.withdrawal_requested_at = 0;

        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.requester_token_account.to_account_info(),
                    to: ctx.accounts.escrow_vault.to_account_info(),
                    authority: ctx.accounts.requester.to_account_info(),
                },
            ),
            amount,
        )?;

        Ok(())
    }

    pub fn top_up_streaming_escrow(ctx: Context<TopUpStreamingEscrow>, amount: u64) -> Result<()> {
        let escrow = &mut ctx.accounts.streaming_escrow;

        escrow.amount_locked = escrow
            .amount_locked
            .checked_add(amount)
            .ok_or(error!(ErrorCode::ArithmeticError))?;

        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.requester_token_account.to_account_info(),
                    to: ctx.accounts.escrow_vault.to_account_info(),
                    authority: ctx.accounts.requester.to_account_info(),
                },
            ),
            amount,
        )?;

        Ok(())
    }

    pub fn claim_streaming_payment(
        ctx: Context<ClaimStreaming>,
        data_size: u64,
        data_hash: [u8; 32],
    ) -> Result<()> {
        let escrow = &mut ctx.accounts.streaming_escrow;
        let provider_clone = &mut ctx.accounts.provider_clone_info;
        let config = &ctx.accounts.config;

        require!(provider_clone.trust_score >= 500, ErrorCode::LowTrustScore);

        let ixs = &ctx.accounts.instructions_sysvar;
        let current_ix = load_instruction_at_checked(0, ixs)?;
        if current_ix.program_id != ed25519_program::ID {
            return Err(error!(ErrorCode::MissingSignatureVerification));
        }

        let mut message = [0u8; 80];
        message[0..32].copy_from_slice(escrow.key().as_ref());
        message[32..40].copy_from_slice(&escrow.amount_claimed.to_le_bytes());
        message[40..48].copy_from_slice(&data_size.to_le_bytes());
        message[48..80].copy_from_slice(&data_hash);

        let mb_count = data_size / 1_048_576;
        let byte_fee = mb_count
            .checked_mul(config.fee_per_mb)
            .ok_or(error!(ErrorCode::ArithmeticError))?;
        let amount_released = if data_size > 0 {
            byte_fee.max(config.min_fee)
        } else {
            0
        };
        let sig_data = &current_ix.data;

        let pubkey_offset = u16::from_le_bytes([sig_data[6], sig_data[7]]) as usize;
        let pubkey_ix_index = u16::from_le_bytes([sig_data[8], sig_data[9]]);
        let msg_offset = u16::from_le_bytes([sig_data[10], sig_data[11]]) as usize;
        let msg_size = u16::from_le_bytes([sig_data[12], sig_data[13]]) as usize;
        let msg_ix_index = u16::from_le_bytes([sig_data[14], sig_data[15]]);
        require!(
            (pubkey_ix_index == 0 || pubkey_ix_index == u16::MAX)
                && (msg_ix_index == 0 || msg_ix_index == u16::MAX),
            ErrorCode::InvalidSignature
        );

        require!(
            sig_data.len() >= msg_offset + msg_size,
            ErrorCode::InvalidSignature
        );
        require!(msg_size == 80, ErrorCode::InvalidSignature);
        require!(
            &sig_data[pubkey_offset..pubkey_offset + 32] == escrow.requester.as_ref(),
            ErrorCode::InvalidSignature
        );
        require!(
            &sig_data[msg_offset..msg_offset + 80] == &message,
            ErrorCode::InvalidSignature
        );

        if amount_released == 0 {
            return Ok(());
        }

        let total_locked = escrow.amount_locked;
        if escrow
            .amount_claimed
            .checked_add(amount_released)
            .ok_or(error!(ErrorCode::ArithmeticError))?
            > total_locked
        {
            return Err(error!(ErrorCode::InsufficientEscrowBalance));
        }

        let treasury_fee = amount_released / 100;
        let provider_payout = amount_released - treasury_fee;

        let escrow_key = escrow.key();
        let seeds = &[
            b"escrow_vault",
            escrow_key.as_ref(),
            &[ctx.bumps.escrow_vault],
        ];
        let signer = &[&seeds[..]];

        token::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.escrow_vault.to_account_info(),
                    to: ctx.accounts.provider_token_account.to_account_info(),
                    authority: ctx.accounts.escrow_vault.to_account_info(),
                },
                signer,
            ),
            provider_payout,
        )?;

        token::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.escrow_vault.to_account_info(),
                    to: ctx.accounts.treasury_token_account.to_account_info(),
                    authority: ctx.accounts.escrow_vault.to_account_info(),
                },
                signer,
            ),
            treasury_fee,
        )?;

        escrow.amount_claimed = escrow.amount_claimed
            .checked_add(amount_released)
            .ok_or(error!(ErrorCode::ArithmeticError))?;

        provider_clone.total_sync_count += 1;
        if provider_clone.trust_score < 2000 {
            provider_clone.trust_score += 1;
        }

        Ok(())
    }

    pub fn flag_provider(
        ctx: Context<FlagProvider>,
    ) -> Result<()> {
        let provider_clone = &mut ctx.accounts.provider_clone_info;
        provider_clone.flag_count += 1;
        provider_clone.trust_score = provider_clone.trust_score.saturating_sub(100);

        Ok(())
    }

    pub fn request_escrow_withdrawal(ctx: Context<ManageEscrow>) -> Result<()> {
        let escrow = &mut ctx.accounts.streaming_escrow;
        escrow.withdrawal_requested_at = Clock::get()?.unix_timestamp;
        Ok(())
    }

    pub fn withdraw_unused_funds(ctx: Context<ManageEscrow>) -> Result<()> {
        let escrow = &mut ctx.accounts.streaming_escrow;
        let now = Clock::get()?.unix_timestamp;

        if escrow.withdrawal_requested_at == 0 {
            return Err(error!(ErrorCode::NoWithdrawalRequested));
        }

        if now < escrow.withdrawal_requested_at + 86400 {
            return Err(error!(ErrorCode::CoolingPeriodActive));
        }

        let remaining = escrow.amount_locked
            .checked_sub(escrow.amount_claimed)
            .ok_or(error!(ErrorCode::ArithmeticError))?;

        let escrow_key = escrow.key();
        let seeds = &[
            b"escrow_vault",
            escrow_key.as_ref(),
            &[ctx.bumps.escrow_vault],
        ];
        let signer = &[&seeds[..]];

        token::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.escrow_vault.to_account_info(),
                    to: ctx.accounts.requester_token_account.to_account_info(),
                    authority: ctx.accounts.escrow_vault.to_account_info(),
                },
                signer,
            ),
            remaining,
        )?;

        escrow.amount_locked = escrow.amount_claimed;
        Ok(())
    }

    pub fn flag_unpaid_request(ctx: Context<FlagUnpaidRequest>) -> Result<()> {
        let requester_clone = &mut ctx.accounts.requester_clone_info;
        requester_clone.trust_score = requester_clone.trust_score.saturating_sub(10);
        msg!("Requester flagged for non-payment. Trust score reduced by 10.");
        Ok(())
    }

    pub fn grant_genesis(ctx: Context<GrantGenesis>) -> Result<()> {
        require!(
            ctx.accounts.clone_info.owner == ctx.accounts.target_owner.key(),
            ErrorCode::Unauthorized
        );
        ctx.accounts.clone_info.is_genesis = true;
        Ok(())
    }

    pub fn revoke_genesis(ctx: Context<GrantGenesis>) -> Result<()> {
        require!(
            ctx.accounts.clone_info.owner == ctx.accounts.target_owner.key(),
            ErrorCode::Unauthorized
        );
        ctx.accounts.clone_info.is_genesis = false;
        Ok(())
    }

    /// Split a single ORBIS payment atomically: 90% tribe owner, 7% clone
    /// operator, 3% treasury. The payer (buyer) is the only signer — we never
    /// custody funds. `payment_ref` is the off-chain correlation/idempotency key
    /// echoed in the emitted event.
    pub fn pay_tribe(ctx: Context<PayTribe>, amount: u64, payment_ref: [u8; 32]) -> Result<()> {
        const CLONE_BPS: u64 = 700;
        const TREASURY_BPS: u64 = 300;

        require!(amount > 0, ErrorCode::ArithmeticError);

        let clone_cut = amount
            .checked_mul(CLONE_BPS)
            .ok_or(error!(ErrorCode::ArithmeticError))?
            / 10_000;
        let treasury_cut = amount
            .checked_mul(TREASURY_BPS)
            .ok_or(error!(ErrorCode::ArithmeticError))?
            / 10_000;
        // Remainder to the owner so no base units are lost to integer rounding (~90%).
        let owner_cut = amount
            .checked_sub(clone_cut)
            .ok_or(error!(ErrorCode::ArithmeticError))?
            .checked_sub(treasury_cut)
            .ok_or(error!(ErrorCode::ArithmeticError))?;

        // 90% -> tribe owner
        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.payer_token_account.to_account_info(),
                    to: ctx.accounts.tribe_owner_token_account.to_account_info(),
                    authority: ctx.accounts.payer.to_account_info(),
                },
            ),
            owner_cut,
        )?;

        // 7% -> clone operator
        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.payer_token_account.to_account_info(),
                    to: ctx.accounts.clone_token_account.to_account_info(),
                    authority: ctx.accounts.payer.to_account_info(),
                },
            ),
            clone_cut,
        )?;

        // 3% -> treasury
        token::transfer(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                Transfer {
                    from: ctx.accounts.payer_token_account.to_account_info(),
                    to: ctx.accounts.treasury_token_account.to_account_info(),
                    authority: ctx.accounts.payer.to_account_info(),
                },
            ),
            treasury_cut,
        )?;

        emit!(TribePaymentMade {
            payer: ctx.accounts.payer.key(),
            payment_ref,
            amount,
            owner_token_account: ctx.accounts.tribe_owner_token_account.key(),
            clone_token_account: ctx.accounts.clone_token_account.key(),
            treasury_token_account: ctx.accounts.treasury_token_account.key(),
            owner_cut,
            clone_cut,
            treasury_cut,
        });

        Ok(())
    }
}

#[derive(Accounts)]
pub struct Initialize<'info> {
    #[account(
        init,
        payer = admin,
        space = 8 + 32 + 32 + 32 + 32 + 8 + 8 + 8 + 8,
        seeds = [b"global_config"],
        bump
    )]
    pub config: Account<'info, GlobalConfig>,
    /// CHECK: merkle tree is validated by the compression program
    pub merkle_tree: AccountInfo<'info>,
    #[account(mut)]
    pub admin: Signer<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct UpdateConfigTree<'info> {
    #[account(
        mut,
        seeds = [b"global_config"],
        bump,
        has_one = admin
    )]
    pub config: Account<'info, GlobalConfig>,
    #[account(mut)]
    pub admin: Signer<'info>,
}

#[derive(Accounts)]
pub struct UpdateConfigFees<'info> {
    #[account(
        mut,
        seeds = [b"global_config"],
        bump,
        has_one = admin
    )]
    pub config: Account<'info, GlobalConfig>,
    pub admin: Signer<'info>,
}

#[derive(Accounts)]
pub struct RegisterClone<'info> {
    #[account(
        init,
        payer = signer,
        space = 201,
        seeds = [b"clone_info", signer.key().as_ref()],
        bump
    )]
    pub clone_info: Account<'info, CloneInfo>,

    #[account(
        mut,
        constraint = signer_token_account.mint == config.orbis_mint
    )]
    pub signer_token_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        constraint = treasury_token_account.owner == config.treasury,
        constraint = treasury_token_account.mint == config.orbis_mint
    )]
    pub treasury_token_account: Account<'info, TokenAccount>,

    #[account(seeds = [b"global_config"], bump)]
    pub config: Account<'info, GlobalConfig>,

    #[account(mut)]
    pub signer: Signer<'info>,

    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
    pub rent: Sysvar<'info, Rent>,
}

#[derive(Accounts)]
pub struct UpdateCloneBaseUrl<'info> {
    #[account(
        mut,
        seeds = [b"clone_info", owner.key().as_ref()],
        bump,
        has_one = owner @ ErrorCode::InvalidSignature
    )]
    pub clone_info: Account<'info, CloneInfo>,

    #[account(mut)]
    pub owner: Signer<'info>,
}

#[derive(Accounts)]
pub struct SyncIndexManifest<'info> {
    /// CHECK: address is constrained to config.merkle_tree; validated by compression program
    #[account(mut, address = config.merkle_tree)]
    pub merkle_tree: AccountInfo<'info>,
    #[account(
        seeds = [b"global_config"],
        bump
    )]
    pub config: Account<'info, GlobalConfig>,

    #[account(
        mut,
        constraint = user_token_account.mint == config.orbis_mint
    )]
    pub user_token_account: Account<'info, TokenAccount>,

    #[account(
        mut,
        constraint = treasury_token_account.owner == config.treasury,
        constraint = treasury_token_account.mint == config.orbis_mint
    )]
    pub treasury_token_account: Account<'info, TokenAccount>,

    #[account(address = config.orbis_mint)]
    pub orbis_mint: Account<'info, Mint>,

    #[account(mut)]
    pub clone_signer: Signer<'info>,
    #[account(
        seeds = [b"clone_info", clone_signer.key().as_ref()],
        bump
    )]
    pub clone_info: Account<'info, CloneInfo>,
    /// CHECK: address is constrained to the known compression program ID
    #[account(address = compression_program::ID)]
    pub compression_program: AccountInfo<'info>,
    /// CHECK: address is constrained to the known noop program ID
    #[account(address = noop_program::ID)]
    pub noop_program: AccountInfo<'info>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct InitStreamingEscrow<'info> {
    #[account(
        init,
        payer = requester,
        space = 8 + 32 + 32 + 8 + 8 + 8,
        seeds = [b"streaming_escrow", requester.key().as_ref(), provider.key().as_ref()],
        bump
    )]
    pub streaming_escrow: Account<'info, StreamingEscrow>,
    #[account(
        init,
        payer = requester,
        seeds = [b"escrow_vault", streaming_escrow.key().as_ref()],
        bump,
        token::mint = orbis_mint,
        token::authority = escrow_vault,
    )]
    pub escrow_vault: Account<'info, TokenAccount>,
    /// CHECK: mint is passed to the token::authority constraint on escrow_vault
    pub orbis_mint: AccountInfo<'info>,
    #[account(mut)]
    pub requester: Signer<'info>,
    #[account(
        seeds = [b"clone_info", requester.key().as_ref()],
        bump
    )]
    pub requester_clone_info: Account<'info, CloneInfo>,
    #[account(mut)]
    pub requester_token_account: Account<'info, TokenAccount>,
    /// CHECK: provider is a participant in the escrow; used only as a key for PDA derivation
    pub provider: AccountInfo<'info>,
    pub token_program: Program<'info, Token>,
    pub system_program: Program<'info, System>,
    pub rent: Sysvar<'info, Rent>,
}

#[derive(Accounts)]
pub struct TopUpStreamingEscrow<'info> {
    #[account(
        mut,
        has_one = requester,
        seeds = [b"streaming_escrow", requester.key().as_ref(), provider.key().as_ref()],
        bump
    )]
    pub streaming_escrow: Account<'info, StreamingEscrow>,
    #[account(mut, seeds = [b"escrow_vault", streaming_escrow.key().as_ref()], bump)]
    pub escrow_vault: Account<'info, TokenAccount>,
    #[account(mut)]
    pub requester: Signer<'info>,
    #[account(
        seeds = [b"clone_info", requester.key().as_ref()],
        bump
    )]
    pub requester_clone_info: Account<'info, CloneInfo>,
    #[account(mut)]
    pub requester_token_account: Account<'info, TokenAccount>,
    /// CHECK: provider is a participant in the escrow; used only as a key for PDA derivation
    pub provider: AccountInfo<'info>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct ClaimStreaming<'info> {
    /// CHECK: requester is verified against the escrow's stored requester field via PDA seeds
    pub requester: AccountInfo<'info>,
    #[account(
        mut,
        seeds = [b"streaming_escrow", requester.key().as_ref(), provider.key().as_ref()],
        bump
    )]
    pub streaming_escrow: Account<'info, StreamingEscrow>,
    #[account(mut, seeds = [b"escrow_vault", streaming_escrow.key().as_ref()], bump)]
    pub escrow_vault: Account<'info, TokenAccount>,
    #[account(
        seeds = [b"clone_info", requester.key().as_ref()],
        bump
    )]
    pub requester_clone_info: Account<'info, CloneInfo>,
    #[account(
        mut,
        seeds = [b"clone_info", provider.key().as_ref()],
        bump
    )]
    pub provider_clone_info: Account<'info, CloneInfo>,
    pub provider: Signer<'info>,
    #[account(
        mut,
        constraint = provider_token_account.owner == provider.key() @ ErrorCode::InvalidTokenAccount,
        constraint = provider_token_account.mint == config.orbis_mint @ ErrorCode::InvalidTokenAccount,
    )]
    pub provider_token_account: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = treasury_token_account.owner == config.treasury @ ErrorCode::InvalidTokenAccount,
        constraint = treasury_token_account.mint == config.orbis_mint @ ErrorCode::InvalidTokenAccount,
    )]
    pub treasury_token_account: Account<'info, TokenAccount>,
    #[account(seeds = [b"global_config"], bump)]
    pub config: Account<'info, GlobalConfig>,
    pub token_program: Program<'info, Token>,
    /// CHECK: address is constrained to the instructions sysvar ID
    #[account(address = ix_sysvar::ID)]
    pub instructions_sysvar: AccountInfo<'info>,
}

#[derive(Accounts)]
pub struct FlagProvider<'info> {
    /// CHECK: requester is verified against streaming_escrow.requester via reporter constraint
    pub requester: AccountInfo<'info>,
    /// CHECK: provider is a participant in the escrow; used only as a key for PDA derivation
    pub provider: AccountInfo<'info>,
    #[account(
        seeds = [b"streaming_escrow", requester.key().as_ref(), provider.key().as_ref()],
        bump
    )]
    pub streaming_escrow: Account<'info, StreamingEscrow>,
    #[account(
        mut,
        seeds = [b"clone_info", provider.key().as_ref()],
        bump
    )]
    pub provider_clone_info: Account<'info, CloneInfo>,
    #[account(
        mut,
        constraint = reporter.key() == streaming_escrow.requester @ ErrorCode::Unauthorized
    )]
    pub reporter: Signer<'info>,
    /// CHECK: merkle tree is not read or written by this instruction
    pub merkle_tree: AccountInfo<'info>,
    /// CHECK: compression program is not called by this instruction
    pub compression_program: AccountInfo<'info>,
}

#[derive(Accounts)]
pub struct FlagUnpaidRequest<'info> {
    /// CHECK: requester is verified against the escrow PDA seeds and has_one constraint
    pub requester: AccountInfo<'info>,
    pub provider: Signer<'info>,
    #[account(
        seeds = [b"streaming_escrow", requester.key().as_ref(), provider.key().as_ref()],
        bump,
        has_one = requester,
        has_one = provider,
    )]
    pub streaming_escrow: Account<'info, StreamingEscrow>,
    #[account(
        mut,
        seeds = [b"clone_info", requester.key().as_ref()],
        bump
    )]
    pub requester_clone_info: Account<'info, CloneInfo>,
}

#[derive(Accounts)]
pub struct ManageEscrow<'info> {
    pub requester: Signer<'info>,
    /// CHECK: provider is a participant in the escrow; used only as a key for PDA derivation
    pub provider: AccountInfo<'info>,
    #[account(
        mut,
        has_one = requester,
        seeds = [b"streaming_escrow", requester.key().as_ref(), provider.key().as_ref()],
        bump
    )]
    pub streaming_escrow: Account<'info, StreamingEscrow>,
    #[account(mut, seeds = [b"escrow_vault", streaming_escrow.key().as_ref()], bump)]
    pub escrow_vault: Account<'info, TokenAccount>,
    #[account(mut)]
    pub requester_token_account: Account<'info, TokenAccount>,
    pub token_program: Program<'info, Token>,
}

#[derive(Accounts)]
pub struct GrantGenesis<'info> {
    #[account(
        mut,
        seeds = [b"clone_info", target_owner.key().as_ref()],
        bump
    )]
    pub clone_info: Account<'info, CloneInfo>,
    /// CHECK: only used for PDA seed derivation
    pub target_owner: AccountInfo<'info>,
    #[account(seeds = [b"global_config"], bump, has_one = admin @ ErrorCode::Unauthorized)]
    pub config: Account<'info, GlobalConfig>,
    pub admin: Signer<'info>,
}

#[account]
pub struct GlobalConfig {
    pub admin: Pubkey,
    pub treasury: Pubkey,
    pub orbis_mint: Pubkey,
    pub merkle_tree: Pubkey,
    pub write_fee: u64,
    pub fee_per_mb: u64,
    pub min_fee: u64,
    pub registration_fee: u64,
}

#[account]
pub struct CloneInfo {
    pub owner: Pubkey,
    pub base_url: String,
    pub trust_score: u32,
    pub total_sync_count: u64,
    pub flag_count: u32,
    pub is_genesis: bool,
}

#[account]
pub struct StreamingEscrow {
    pub requester: Pubkey,
    pub provider: Pubkey,
    pub amount_locked: u64,
    pub amount_claimed: u64,
    pub withdrawal_requested_at: i64,
}

#[error_code]
pub enum ErrorCode {
    #[msg("Insufficient funds in escrow")]
    InsufficientEscrowBalance,
    #[msg("No withdrawal has been requested")]
    NoWithdrawalRequested,
    #[msg("Withdrawal is locked during cooling period")]
    CoolingPeriodActive,
    #[msg("Invalid signature provided")]
    InvalidSignature,
    #[msg("Missing required Ed25519 signature verification")]
    MissingSignatureVerification,
    #[msg("Provider trust score is too low to claim payments")]
    LowTrustScore,
    #[msg("Arithmetic error during fee calculation")]
    ArithmeticError,
    #[msg("Requester and provider cannot be the same (self-payment)")]
    SelfPaymentNotAllowed,
    #[msg("Token account has wrong owner or mint")]
    InvalidTokenAccount,
    #[msg("Caller is not authorized to perform this action")]
    Unauthorized,
    #[msg("Base URL exceeds maximum allowed length")]
    UrlTooLong,
    #[msg("Batch contains too many entries")]
    BatchTooLarge,
}

#[event]
pub struct IndexManifestCommitted {
    pub packet_type: u8,
    pub batch_id: u32,
    pub manifest_hash: [u8; 32],
    pub payload_hash: [u8; 32],
    pub manifest_pointer: String,
    pub entry_count: u32,
    pub action_count: u32,
    pub from_ts: i64,
    pub to_ts: i64,
    pub provider: Pubkey,
}

#[derive(Accounts)]
pub struct PayTribe<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    #[account(
        mut,
        constraint = payer_token_account.owner == payer.key() @ ErrorCode::InvalidTokenAccount,
        constraint = payer_token_account.mint == config.orbis_mint @ ErrorCode::InvalidTokenAccount,
    )]
    pub payer_token_account: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = tribe_owner_token_account.mint == config.orbis_mint @ ErrorCode::InvalidTokenAccount,
    )]
    pub tribe_owner_token_account: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = clone_token_account.mint == config.orbis_mint @ ErrorCode::InvalidTokenAccount,
    )]
    pub clone_token_account: Account<'info, TokenAccount>,
    #[account(
        mut,
        constraint = treasury_token_account.owner == config.treasury @ ErrorCode::InvalidTokenAccount,
        constraint = treasury_token_account.mint == config.orbis_mint @ ErrorCode::InvalidTokenAccount,
    )]
    pub treasury_token_account: Account<'info, TokenAccount>,
    #[account(seeds = [b"global_config"], bump)]
    pub config: Account<'info, GlobalConfig>,
    pub token_program: Program<'info, Token>,
}

#[event]
pub struct TribePaymentMade {
    pub payer: Pubkey,
    pub payment_ref: [u8; 32],
    pub amount: u64,
    pub owner_token_account: Pubkey,
    pub clone_token_account: Pubkey,
    pub treasury_token_account: Pubkey,
    pub owner_cut: u64,
    pub clone_cut: u64,
    pub treasury_cut: u64,
}
