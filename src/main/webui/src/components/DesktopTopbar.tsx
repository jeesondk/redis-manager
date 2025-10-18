import {Separator} from "@/components/ui/separator.tsx";
import {
    Breadcrumb,
    BreadcrumbItem,
    BreadcrumbLink,
    BreadcrumbList,
    BreadcrumbSeparator
} from "@/components/ui/breadcrumb.tsx";
import * as React from "react";
import {ChevronRight, LogOut, User, Mail, Shield, Clock} from "lucide-react";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger
} from "@/components/ui/dropdown-menu.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Avatar, AvatarFallback, AvatarImage} from "@/components/ui/avatar.tsx";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog.tsx";
import { logoutAndRedirect } from "@/lib/auth/logout";

type DesktopTopbar = {
    userName: string;
    userAvatarUrl: string;
    breadcrumbs: {
        label: string;
        href?: string;
    }[];
    logo: React.ReactNode;
};
export function DesktopTopbar({
                                  userName,
                                  userAvatarUrl,
                                  breadcrumbs,
                                  logo
                              }: Required<Pick<DesktopTopbar, "userName" | "breadcrumbs" | "logo">> & Pick<DesktopTopbar, "userAvatarUrl">) {
    const [showProfileModal, setShowProfileModal] = React.useState(false);
    const [userInfo, setUserInfo] = React.useState<{username: string} | null>(null);
    const [authProvider, setAuthProvider] = React.useState<string>('');
    const [sessionLoaded, setSessionLoaded] = React.useState(false);

    // Fetch user session info when modal opens
    React.useEffect(() => {
        if (showProfileModal && !sessionLoaded) {
            Promise.all([
                fetch('/api/auth/me', { credentials: 'include' })
                    .then(res => res.ok ? res.json() : null)
                    .catch(() => null),
                fetch('/api/auth/provider', { credentials: 'include' })
                    .then(res => res.ok ? res.json() : null)
                    .catch(() => null)
            ]).then(([user, provider]) => {
                setUserInfo(user);
                setAuthProvider(provider?.provider || 'Unknown');
                setSessionLoaded(true);
            });
        }
    }, [showProfileModal, sessionLoaded]);

    const handleProfileClick = () => {
        setShowProfileModal(true);
    };

    return (
        <>
        <div className="hidden md:flex h-14 w-full items-center justify-between border-b bg-white px-4">
            {/* Left: Logo + Breadcrumbs */}
            <div className="flex min-w-0 items-center gap-4">
                <div className="flex items-center gap-2 shrink-0">
                    {logo}
                    <Separator orientation="vertical" className="h-6"/>
                </div>
                <div className="min-w-0">
                    <Breadcrumb>
                        <BreadcrumbList className="flex items-center gap-1 text-sm text-muted-foreground">
                            {breadcrumbs.map((c, i) => (
                                <React.Fragment key={i}>
                                    <BreadcrumbItem className="truncate max-w-[12rem]">
                                        {c.href ? (
                                            <BreadcrumbLink href={c.href} className="truncate">
                                                {c.label}
                                            </BreadcrumbLink>
                                        ) : (
                                            <span className="truncate">{c.label}</span>
                                        )}
                                    </BreadcrumbItem>
                                    {i < breadcrumbs.length - 1 && (
                                        <BreadcrumbSeparator>
                                            <ChevronRight className="h-3.5 w-3.5"/>
                                        </BreadcrumbSeparator>
                                    )}
                                </React.Fragment>
                            ))}
                        </BreadcrumbList>
                    </Breadcrumb>
                </div>
            </div>

            {/* Right: User */}
            <div className="flex items-center gap-3">
                <span className="hidden lg:block text-sm text-muted-foreground">{userName}</span>
                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="rounded-full">
                            <Avatar className="h-8 w-8">
                                <AvatarImage src={userAvatarUrl} alt={userName}/>
                                <AvatarFallback>
                                    {userName
                                        .split(" ")
                                        .map((s) => s[0])
                                        .join("")
                                        .slice(0, 2)
                                        .toUpperCase()}
                                </AvatarFallback>
                            </Avatar>
                        </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" className="w-56">
                        <DropdownMenuLabel className="font-normal">
                            <div className="flex flex-col">
                                <span className="text-sm font-medium leading-none">{userName}</span>
                                <span className="text-xs text-muted-foreground">Signed in</span>
                            </div>
                        </DropdownMenuLabel>
                        <DropdownMenuSeparator/>
                        <DropdownMenuItem onSelect={handleProfileClick}>
                            <User className="mr-2 h-4 w-4"/> Profile
                        </DropdownMenuItem>
                        <DropdownMenuSeparator/>
                        <DropdownMenuItem className="text-red-600" onSelect={(e)=>{e.preventDefault(); logoutAndRedirect('/')}}>
                            <LogOut className="mr-2 h-4 w-4"/> Log out
                        </DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
            </div>
        </div>

        {/* User Profile Modal */}
        <Dialog open={showProfileModal} onOpenChange={setShowProfileModal}>
            <DialogContent className="sm:max-w-md">
                <DialogHeader>
                    <DialogTitle>User Profile</DialogTitle>
                    <DialogDescription>
                        View your session and account information
                    </DialogDescription>
                </DialogHeader>
                <div className="space-y-6 py-4">
                    {/* User Avatar and Name */}
                    <div className="flex items-center gap-4">
                        <Avatar className="h-16 w-16">
                            <AvatarImage src={userAvatarUrl} alt={userName}/>
                            <AvatarFallback className="text-lg">
                                {userName
                                    .split(" ")
                                    .map((s) => s[0])
                                    .join("")
                                    .slice(0, 2)
                                    .toUpperCase()}
                            </AvatarFallback>
                        </Avatar>
                        <div>
                            <h3 className="text-lg font-semibold">{userName}</h3>
                            <p className="text-sm text-muted-foreground">Active session</p>
                        </div>
                    </div>

                    {/* Session Information */}
                    <div className="space-y-3">
                        <div className="flex items-start gap-3 rounded-lg border p-3">
                            <User className="h-5 w-5 text-muted-foreground mt-0.5" />
                            <div className="flex-1 space-y-1">
                                <p className="text-sm font-medium">Username</p>
                                <p className="text-sm text-muted-foreground">
                                    {userInfo?.username || userName || 'Loading...'}
                                </p>
                            </div>
                        </div>

                        <div className="flex items-start gap-3 rounded-lg border p-3">
                            <Shield className="h-5 w-5 text-muted-foreground mt-0.5" />
                            <div className="flex-1 space-y-1">
                                <p className="text-sm font-medium">Authentication Provider</p>
                                <p className="text-sm text-muted-foreground">
                                    {authProvider || 'Loading...'}
                                </p>
                            </div>
                        </div>

                        <div className="flex items-start gap-3 rounded-lg border p-3">
                            <Clock className="h-5 w-5 text-muted-foreground mt-0.5" />
                            <div className="flex-1 space-y-1">
                                <p className="text-sm font-medium">Session Status</p>
                                <p className="text-sm text-muted-foreground">
                                    Active • 30 min timeout
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="flex justify-end gap-2 pt-2">
                        <Button 
                            variant="outline" 
                            onClick={() => setShowProfileModal(false)}
                        >
                            Close
                        </Button>
                    </div>
                </div>
            </DialogContent>
        </Dialog>
        </>
    )
}