import { Box, Skeleton, Stack } from '@mui/material';

export default function LoadingFallback() {
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        sx={{
          minHeight: 116,
          borderBottom: 1,
          borderColor: 'divider',
          px: { xs: 1.75, md: 2.5, lg: 3 },
          py: 2,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 1.25,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Skeleton variant="rectangular" width={42} height={42} />
          <Box sx={{ flexGrow: 1, maxWidth: 640 }}>
            <Skeleton variant="text" width={120} height={18} />
            <Skeleton variant="text" width="32%" height={34} />
            <Skeleton variant="text" width="82%" height={18} />
          </Box>
          <Skeleton variant="circular" width={40} height={40} />
          <Skeleton variant="circular" width={40} height={40} />
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Skeleton variant="rectangular" width={116} height={28} />
          <Skeleton variant="rectangular" width={150} height={28} />
          <Skeleton variant="rectangular" width={152} height={28} />
          <Skeleton variant="rectangular" width={136} height={28} />
        </Stack>
      </Box>

      <Box sx={{ display: 'flex', minHeight: 'calc(100vh - 112px)' }}>
        <Box
          sx={{
            width: 296,
            borderRight: 1,
            borderColor: 'divider',
            p: 2,
            display: { xs: 'none', md: 'block' },
          }}
        >
          <Stack spacing={2}>
            <Skeleton variant="rectangular" height={136} />
            <Box>
              <Skeleton variant="text" width={72} height={18} sx={{ mb: 1 }} />
              <Stack spacing={1}>
                <Skeleton variant="rectangular" height={56} />
                <Skeleton variant="rectangular" height={56} />
              </Stack>
            </Box>
            <Box>
              <Skeleton variant="text" width={72} height={18} sx={{ mb: 1 }} />
              <Stack spacing={1}>
                <Skeleton variant="rectangular" height={56} />
                <Skeleton variant="rectangular" height={56} />
                <Skeleton variant="rectangular" height={56} />
              </Stack>
            </Box>
            <Skeleton variant="rectangular" height={88} />
          </Stack>
        </Box>

        <Box sx={{ flexGrow: 1, p: { xs: 2, md: 3, lg: 4 } }}>
          <Box sx={{ maxWidth: 1560, mx: 'auto' }}>
            <Stack spacing={2.5}>
              <Skeleton variant="rectangular" height={128} />
              <Stack direction={{ xs: 'column', xl: 'row' }} spacing={2}>
                <Skeleton variant="rectangular" height={176} sx={{ flex: 1 }} />
                <Skeleton variant="rectangular" height={176} sx={{ flex: 1 }} />
                <Skeleton variant="rectangular" height={176} sx={{ flex: 1 }} />
              </Stack>
              <Stack direction={{ xs: 'column', xl: 'row' }} spacing={2}>
                <Skeleton variant="rectangular" height={372} sx={{ flex: 1.15 }} />
                <Skeleton variant="rectangular" height={372} sx={{ flex: 0.85 }} />
              </Stack>
              <Skeleton variant="rectangular" height={360} />
            </Stack>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
